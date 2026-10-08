package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.listener.TradeSettingsCacheListener;
import com.ultikits.plugins.trade.testsupport.InterleavingOperator;
import com.ultikits.plugins.trade.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The player-scoped settings cache and the end of a contended change
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/54">UltiTrade#54</a>, plan 17-83 Task 2;
 * maintainer decision 2026-10-06 00:04: a read-only cache scoped to the player's time on this server).
 *
 * <ul>
 *   <li>The cache holds a player only while they are online here: loaded on join, or on first use while
 *       online; dropped on quit; a quit writes nothing. A read for a player who is not online here reads
 *       the database and adds no entry.</li>
 *   <li>A row that changes on every attempt ends the change after {@link TradeLogService#MAX_WRITE_ATTEMPTS}
 *       attempts as {@code BUSY}, with the write-failed line naming the player and the contention reason;
 *       nothing is written from a stale copy. A storage error ends it as {@code FAILED}.</li>
 * </ul>
 */
@DisplayName("Trade settings cache lifetime and contended changes (UltiTrade#54)")
class TradeSettingsCacheLifetimeTest {

    @TempDir
    Path dir;

    private SharedSqliteDatabase database;
    private DataOperator<PlayerTradeSettings> table;
    private UUID playerUuid;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(PlayerTradeSettings.class);
        playerUuid = UUID.randomUUID();
        player = UltiTradeTestHelper.createMockPlayer("Visitor", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    private TradeLogService server() throws Exception {
        TradeLogService service = new TradeLogService();
        UltiTradeTestHelper.setField(service, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(service, "config", UltiTradeTestHelper.createDefaultConfig());
        UltiTradeTestHelper.setField(service, "settingsOperator", database.openAs(PlayerTradeSettings.class));
        return service;
    }

    private Map<UUID, PlayerTradeSettings> cacheOf(TradeLogService service) throws Exception {
        return UltiTradeTestHelper.getField(service, "settingsCache");
    }

    private PlayerTradeSettings storedRow() {
        List<PlayerTradeSettings> rows = table.getAll(
                WhereCondition.builder().column("player_uuid").value(playerUuid.toString()).build());
        assertThat(rows).as("exactly one stored row for the player").hasSize(1);
        return rows.get(0);
    }

    /** Server B's change, written straight into the table as another server's statement would be. */
    private void otherServerTurnsTradingOff() {
        PlayerTradeSettings row = storedRow();
        row.setTradeEnabled(false);
        assertThat(table.updateCounted(row)).isEqualTo(1);
    }

    /** {@code operator}, counting every call whose name starts with {@code update} or {@code insert}. */
    @SuppressWarnings("unchecked")
    private static DataOperator<PlayerTradeSettings> countingWrites(DataOperator<PlayerTradeSettings> operator, AtomicInteger writes) {
        return (DataOperator<PlayerTradeSettings>) Proxy.newProxyInstance(DataOperator.class.getClassLoader(),
                new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("update") || method.getName().startsWith("insert")) {
                        writes.incrementAndGet();
                    }
                    try {
                        return method.invoke(operator, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    @Nested
    @DisplayName("The cache holds a player only while they are online on this server")
    class Lifetime {

        @Test
        @DisplayName("A join loads the player's stored settings into the cache")
        void joinLoads() throws Exception {
            server().toggleTrade(player); // a row exists, trading off
            TradeLogService serverA = server();

            serverA.playerJoined(player);

            assertThat(cacheOf(serverA)).containsKey(playerUuid);
            assertThat(cacheOf(serverA).get(playerUuid).isTradeEnabled()).isFalse();
        }

        @Test
        @DisplayName("A quit drops the player and writes nothing; a change another server made is then read, not the old copy")
        void quitDropsAndWritesNothing() throws Exception {
            server().toggleTrade(player);
            server().toggleTrade(player); // on
            TradeLogService serverA = server();
            AtomicInteger writes = new AtomicInteger();
            DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(serverA, "settingsOperator");
            UltiTradeTestHelper.setField(serverA, "settingsOperator", countingWrites(own, writes));
            serverA.playerJoined(player);
            otherServerTurnsTradingOff();

            serverA.playerQuit(playerUuid);

            assertThat(writes.get()).as("a quit writes nothing").isZero();
            assertThat(cacheOf(serverA)).doesNotContainKey(playerUuid);
            assertThat(storedRow().isTradeEnabled()).as("the other server's change is untouched").isFalse();
            assertThat(serverA.isTradeEnabled(playerUuid)).as("read from the database now").isFalse();
        }

        @Test
        @DisplayName("A read for a player who is not online here reads the database and adds no cache entry")
        void readForAPlayerNotOnlineIsNotCached() throws Exception {
            server().toggleTrade(player); // off
            TradeLogService serverA = server();

            assertThat(serverA.getPlayerStats(playerUuid).isTradeEnabled()).isFalse();
            assertThat(serverA.isTradeEnabled(playerUuid)).isFalse();

            assertThat(cacheOf(serverA)).as("no entry for a player who is not online here").isEmpty();
        }

        @Test
        @DisplayName("The first read for a player who is online here (joined before the module loaded) caches them")
        void firstUseWhileOnlineCaches() throws Exception {
            server().toggleTrade(player); // off
            TradeLogService serverA = server();
            doReturn(player).when(Bukkit.getServer()).getPlayer(playerUuid);

            assertThat(serverA.isTradeEnabled(playerUuid)).isFalse();

            assertThat(cacheOf(serverA)).containsKey(playerUuid);
        }

        @Test
        @DisplayName("A first read off the main thread adds no entry, even for a player online here (a placeholder evaluated as they quit; gate-1 F4 of PR #66)")
        void firstReadOffTheMainThreadAddsNoEntry() throws Exception {
            server().toggleTrade(player); // off
            TradeLogService serverA = server();
            doReturn(player).when(Bukkit.getServer()).getPlayer(playerUuid);
            boolean[] read = new boolean[1];

            Thread placeholder = new Thread(() -> read[0] = !serverA.isTradeEnabled(playerUuid));
            placeholder.start();
            placeholder.join();

            assertThat(read[0]).as("the read itself works: trading off").isTrue();
            assertThat(cacheOf(serverA)).as("no entry added off the main thread").isEmpty();
        }

        @Test
        @DisplayName("After a change the cached entry is the row as written")
        void changeReplacesTheEntry() throws Exception {
            server().toggleTrade(player);
            server().toggleTrade(player); // on
            TradeLogService serverA = server();
            serverA.playerJoined(player);
            UUID blocked = UUID.randomUUID();

            serverA.blockPlayer(player, blocked);

            PlayerTradeSettings cached = cacheOf(serverA).get(playerUuid);
            assertThat(cached.isBlocked(blocked.toString())).isTrue();
            assertThat(cached.getBlockedPlayersJson()).isEqualTo(storedRow().getBlockedPlayersJson());
        }

        @Test
        @DisplayName("A change for a player who is not cached adds no cache entry")
        void changeAddsNoEntry() throws Exception {
            TradeLogService serverA = server();

            serverA.toggleTrade(player);

            assertThat(cacheOf(serverA)).isEmpty();
            assertThat(storedRow().isTradeEnabled()).isFalse();
        }

        @Test
        @DisplayName("A change by a player online here caches the row as written, also their first row (so a deleted row can be re-created with it, UltiTrade#57)")
        void changeWhileOnlineCachesTheWrittenRow() throws Exception {
            TradeLogService serverA = server();
            doReturn(player).when(Bukkit.getServer()).getPlayer(playerUuid);
            serverA.playerJoined(player); // no row yet: nothing cached

            serverA.toggleTrade(player);

            assertThat(cacheOf(serverA)).containsKey(playerUuid);
            assertThat(cacheOf(serverA).get(playerUuid).isTradeEnabled()).isFalse();
        }

        @Test
        @DisplayName("A join under a new name stores the new name and keeps every other stored value")
        void joinUnderANewNameStoresIt() throws Exception {
            server().toggleTrade(player); // off, stored as "Visitor"
            TradeLogService serverA = server();
            Player renamed = UltiTradeTestHelper.createMockPlayer("Revisitor", playerUuid);

            serverA.playerJoined(renamed);

            PlayerTradeSettings row = storedRow();
            assertThat(row.getPlayerName()).isEqualTo("Revisitor");
            assertThat(row.isTradeEnabled()).isFalse();
        }

        @Test
        @DisplayName("Shutdown empties the cache and writes nothing")
        void shutdownWritesNothing() throws Exception {
            server().toggleTrade(player); // off
            TradeLogService serverA = server();
            AtomicInteger writes = new AtomicInteger();
            DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(serverA, "settingsOperator");
            UltiTradeTestHelper.setField(serverA, "settingsOperator", countingWrites(own, writes));
            serverA.playerJoined(player);

            serverA.shutdown();

            assertThat(writes.get()).isZero();
            assertThat(cacheOf(serverA)).isEmpty();
        }
    }

    @Nested
    @DisplayName("A contended or failed change writes nothing")
    class Contention {

        @Test
        @DisplayName("A row another server changes before every attempt: BUSY after three attempts, the contention line names the player, nothing of this server's is written")
        void contendedChangeEndsBusy() throws Exception {
            TradeLogService serverB = server();
            serverB.toggleTrade(player);
            serverB.toggleTrade(player); // on
            TradeLogService serverA = server();
            UUID[] lastBlockedByB = new UUID[1];
            AtomicInteger hooks = new AtomicInteger();
            DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(serverA, "settingsOperator");
            UltiTradeTestHelper.setField(serverA, "settingsOperator", InterleavingOperator.beforeUpdates(own, Integer.MAX_VALUE, () -> {
                lastBlockedByB[0] = UUID.randomUUID();
                serverB.blockPlayer(player, lastBlockedByB[0]);
                hooks.incrementAndGet();
            }));

            TradeLogService.SettingsChangeResult result = serverA.toggle(player);

            assertThat(result.getWrite()).isEqualTo(TradeLogService.SettingsWrite.BUSY);
            assertThat(hooks.get()).as("one attempt per bound").isEqualTo(TradeLogService.MAX_WRITE_ATTEMPTS);
            PlayerTradeSettings row = storedRow();
            assertThat(row.isTradeEnabled()).as("server A's toggle was not written").isTrue();
            assertThat(row.isBlocked(lastBlockedByB[0].toString())).as("server B's last change stands").isTrue();
            String contended = CatalogueText.text("zh", "log_settings_save_failed").replace("{PLAYER}", "Visitor")
                    + ": " + CatalogueText.text("zh", "log_settings_write_contended");
            verify(UltiTradeTestHelper.getMockLogger()).warn(contended);
        }

        @Test
        @DisplayName("A storage error: FAILED, logged with the exception and the player's name, nothing written")
        void storageErrorEndsFailed() throws Exception {
            server().toggleTrade(player); // off
            TradeLogService serverA = server();
            DataOperator<PlayerTradeSettings> failing = failingUpdates(UltiTradeTestHelper.getField(serverA, "settingsOperator"));
            UltiTradeTestHelper.setField(serverA, "settingsOperator", failing);

            assertThat(serverA.unblock(player, UUID.randomUUID())).as("nothing to unblock is UNCHANGED, not a failure")
                    .isEqualTo(TradeLogService.SettingsWrite.UNCHANGED);
            TradeLogService.SettingsChangeResult result = serverA.toggle(player);

            assertThat(result.getWrite()).isEqualTo(TradeLogService.SettingsWrite.FAILED);
            assertThat(storedRow().isTradeEnabled()).isFalse();
            verify(UltiTradeTestHelper.getMockLogger()).warn(any(RuntimeException.class),
                    eq(CatalogueText.text("zh", "log_settings_save_failed").replace("{PLAYER}", "Visitor")));
        }

        @Test
        @DisplayName("toggleTrade reports the stored state when its write did not happen, not the state it asked for")
        void toggleTradeReportsTheStoredStateWhenNotWritten() throws Exception {
            server().toggleTrade(player); // off
            TradeLogService serverA = server();
            UltiTradeTestHelper.setField(serverA, "settingsOperator",
                    failingUpdates(UltiTradeTestHelper.getField(serverA, "settingsOperator")));

            assertThat(serverA.toggleTrade(player)).as("still off: nothing was written").isFalse();
            verify(UltiTradeTestHelper.getMockLogger()).warn(any(RuntimeException.class), startsWith(
                    CatalogueText.text("zh", "log_settings_save_failed").replace("{PLAYER}", "Visitor")));
        }

        @SuppressWarnings("unchecked")
        private DataOperator<PlayerTradeSettings> failingUpdates(DataOperator<PlayerTradeSettings> operator) {
            return (DataOperator<PlayerTradeSettings>) Proxy.newProxyInstance(DataOperator.class.getClassLoader(),
                    new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                        if (method.getName().startsWith("update")) {
                            throw new IllegalStateException("storage down");
                        }
                        try {
                            return method.invoke(operator, args);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    @Nested
    @DisplayName("Cache publication follows the order of this server's writes (Codex run 1, P2; gate-1 F3)")
    class PublicationOrder {

        @Test
        @DisplayName("A background statistics write that publishes after the player's newer toggle does not put the older row back into the cache")
        void olderBackgroundWriteDoesNotOverwriteANewerEntry() throws Exception {
            server().toggleTrade(player);
            server().toggleTrade(player); // on
            TradeLogService serverA = server();
            UltiTradeTestHelper.setField(serverA, "logOperator", database.openAs(com.ultikits.plugins.trade.entity.TradeLogData.class));
            doReturn(player).when(Bukkit.getServer()).getPlayer(playerUuid);
            serverA.playerJoined(player);
            Thread main = Thread.currentThread();
            java.util.concurrent.CountDownLatch written = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch toggled = new java.util.concurrent.CountDownLatch(1);
            DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(serverA, "settingsOperator");
            UltiTradeTestHelper.setField(serverA, "settingsOperator", InterleavingOperator.afterFirstUpdateOffThread(own, main, () -> {
                written.countDown();
                try {
                    // At most 500 ms: where this server orders a player's writes, the toggle waits for this write to
                    // be published and cannot finish inside the window (Codex run 2 route change).
                    toggled.await(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            Player partner = UltiTradeTestHelper.createMockPlayer("Partner", UUID.randomUUID());
            Thread statistics = new Thread(() -> serverA.logCompletedTrade(
                    new com.ultikits.plugins.trade.entity.TradeSession(player, partner), player, partner, 0.0, 0));

            statistics.start();
            assertThat(written.await(10, java.util.concurrent.TimeUnit.SECONDS)).as("the background write happened").isTrue();
            // The player's own, newer change (trading off), made while the background write is in its window
            Thread toggle = new Thread(() -> {
                serverA.toggleTrade(player);
                toggled.countDown();
            });
            toggle.start();
            toggle.join();
            statistics.join();

            PlayerTradeSettings stored = storedRow();
            assertThat(stored.isTradeEnabled()).as("stored: off").isFalse();
            assertThat(stored.getTotalTrades()).as("stored: the trade counted").isEqualTo(1);
            assertThat(cacheOf(serverA).get(playerUuid).isTradeEnabled())
                    .as("the cache agrees with the stored row: the older background row did not replace the toggle").isFalse();
        }

        @Test
        @DisplayName("Two background statistics writes for one player overlap: the cache ends with the last stored row, not the earlier one (Codex run 2, P2)")
        void overlappingBackgroundWritesPublishInWriteOrder() throws Exception {
            server().toggleTrade(player);
            server().toggleTrade(player); // on, a row exists
            TradeLogService serverA = server();
            UltiTradeTestHelper.setField(serverA, "logOperator", database.openAs(com.ultikits.plugins.trade.entity.TradeLogData.class));
            doReturn(player).when(Bukkit.getServer()).getPlayer(playerUuid);
            serverA.playerJoined(player);
            Player partner = UltiTradeTestHelper.createMockPlayer("Partner", UUID.randomUUID());
            Runnable oneTrade = () -> serverA.logCompletedTrade(
                    new com.ultikits.plugins.trade.entity.TradeSession(player, partner), player, partner, 0.0, 0);
            Thread[] second = new Thread[1];
            DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(serverA, "settingsOperator");
            // The first task pauses right after its write; the second task runs in that pause (or, when this server
            // orders its writes for a player, waits for the first to publish -- the pause gives it 500 ms either way).
            UltiTradeTestHelper.setField(serverA, "settingsOperator", InterleavingOperator.afterFirstUpdateOffThread(own, Thread.currentThread(), () -> {
                second[0] = new Thread(oneTrade);
                second[0].start();
                try {
                    second[0].join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            Thread first = new Thread(oneTrade);

            first.start();
            first.join();
            second[0].join();

            PlayerTradeSettings stored = storedRow();
            assertThat(stored.getTotalTrades()).as("both trades counted").isEqualTo(2);
            assertThat(cacheOf(serverA).get(playerUuid).getTotalTrades())
                    .as("the cache holds the last stored row").isEqualTo(stored.getTotalTrades());
        }
    }

    @Nested
    @DisplayName("The listener drives the lifetime")
    class Listener {

        @Test
        @DisplayName("A join event loads the player; a quit event drops them")
        void joinAndQuitEvents() throws Exception {
            TradeLogService service = mock(TradeLogService.class);
            TradeSettingsCacheListener listener = new TradeSettingsCacheListener();
            UltiTradeTestHelper.setField(listener, "logService", service);

            PlayerJoinEvent join = mock(PlayerJoinEvent.class);
            doReturn(player).when(join).getPlayer();
            PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
            doReturn(player).when(quit).getPlayer();

            listener.onPlayerJoin(join);
            listener.onPlayerQuit(quit);

            verify(service).playerJoined(player);
            verify(service).playerQuit(playerUuid);
        }
    }
}
