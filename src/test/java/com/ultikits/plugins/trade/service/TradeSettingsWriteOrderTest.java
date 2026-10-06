package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.entity.TradeLogData;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.testsupport.InterleavingOperator;
import com.ultikits.plugins.trade.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * One player's settings writes on one server: ordered per player, and the main thread waits at most about one tick
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/54">UltiTrade#54</a>; maintainer decision 2026-10-06 on the
 * main-thread trade-off, after the gate-1 top-up review of PR #66, T1/T2).
 *
 * <ul>
 *   <li>A lock per player: no player waits for another player's write.</li>
 *   <li>The main thread waits about 50 ms for the player's lock: a command that cannot get it answers BUSY (nothing
 *       written), a join or an uncached read that cannot get it reads without caching. Background writes wait as long as
 *       needed, never on the main thread.</li>
 *   <li>Lock entries do not outlive their use: when nothing holds or waits for a player's lock, it is gone.</li>
 * </ul>
 * Each test holds a player's lock from a background statistics write paused inside its write (at most 3 s), and
 * measures what the main thread does meanwhile.
 */
@DisplayName("Per-player write order with a bounded main-thread wait (UltiTrade#54)")
class TradeSettingsWriteOrderTest {

    private static final long HOLD_MILLIS = 3000;

    @TempDir
    Path dir;

    private SharedSqliteDatabase database;
    private DataOperator<PlayerTradeSettings> table;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(PlayerTradeSettings.class);
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
        UltiTradeTestHelper.setField(service, "logOperator", database.openAs(TradeLogData.class));
        return service;
    }

    private PlayerTradeSettings storedRow(UUID playerUuid) {
        List<PlayerTradeSettings> rows = table.getAll(
                WhereCondition.builder().column("player_uuid").value(playerUuid.toString()).build());
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static Map<UUID, PlayerTradeSettings> cacheOf(TradeLogService service) throws Exception {
        return UltiTradeTestHelper.getField(service, "settingsCache");
    }

    /** A player whose UUID fell into the same of the 64 shared stripes as {@code other}'s, before per-player locks. */
    private static UUID sameStripeAs(UUID other) {
        int stripe = (other.hashCode() & 0x7fffffff) % 64;
        while (true) {
            UUID candidate = UUID.randomUUID();
            if ((candidate.hashCode() & 0x7fffffff) % 64 == stripe && !candidate.equals(other)) {
                return candidate;
            }
        }
    }

    /**
     * Start a background statistics write for {@code holder} that pauses inside its write -- holding the player's lock --
     * until {@code release} counts down or {@link #HOLD_MILLIS} pass. Returns once the pause has begun.
     */
    private Thread holdWhileWriting(TradeLogService service, Player holder, CountDownLatch release) throws Exception {
        CountDownLatch paused = new CountDownLatch(1);
        DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(service, "settingsOperator");
        UltiTradeTestHelper.setField(service, "settingsOperator", InterleavingOperator.afterFirstUpdateOffThread(own, Thread.currentThread(), () -> {
            paused.countDown();
            try {
                release.await(HOLD_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        Player partner = UltiTradeTestHelper.createMockPlayer("Partner", UUID.randomUUID());
        Thread statistics = new Thread(() -> service.logCompletedTrade(new TradeSession(holder, partner), holder, partner, 0.0, 0));
        statistics.start();
        assertThat(paused.await(10, TimeUnit.SECONDS)).as("the background write is in progress").isTrue();
        return statistics;
    }

    @Test
    @DisplayName("No player waits for another player's write: Y's toggle completes while X's statistics write is in progress (X and Y shared a stripe before)")
    void noCrossPlayerWait() throws Exception {
        UUID xId = UUID.randomUUID();
        Player x = UltiTradeTestHelper.createMockPlayer("X", xId);
        Player y = UltiTradeTestHelper.createMockPlayer("Y", sameStripeAs(xId));
        TradeLogService serverA = server();
        serverA.toggleTrade(x);
        serverA.toggleTrade(x); // X has a row
        CountDownLatch release = new CountDownLatch(1);
        Thread statistics = holdWhileWriting(serverA, x, release);

        long started = System.nanoTime();
        TradeLogService.SettingsChangeResult result = serverA.toggle(y);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        release.countDown();
        statistics.join();

        assertThat(result.getWrite()).isEqualTo(TradeLogService.SettingsWrite.WRITTEN);
        assertThat(elapsedMillis).as("Y did not wait for X's write").isLessThan(1000);
        assertThat(storedRow(y.getUniqueId()).isTradeEnabled()).isFalse();
    }

    @Test
    @DisplayName("A main-thread toggle while the player's own background write is in progress answers BUSY within about a tick and writes nothing; ordering still holds afterwards")
    void mainThreadCommandTimesOutAsBusy() throws Exception {
        UUID xId = UUID.randomUUID();
        Player x = UltiTradeTestHelper.createMockPlayer("X", xId);
        TradeLogService serverA = server();
        serverA.toggleTrade(x);
        serverA.toggleTrade(x); // on
        doReturn(x).when(Bukkit.getServer()).getPlayer(xId);
        serverA.playerJoined(x);
        CountDownLatch release = new CountDownLatch(1);
        Thread statistics = holdWhileWriting(serverA, x, release);

        long started = System.nanoTime();
        TradeLogService.SettingsChangeResult busy = serverA.toggle(x);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        release.countDown();
        statistics.join();

        assertThat(busy.getWrite()).isEqualTo(TradeLogService.SettingsWrite.BUSY);
        assertThat(elapsedMillis).as("the main thread waited about one tick, not for the write").isLessThan(1000);
        PlayerTradeSettings stored = storedRow(xId);
        assertThat(stored.isTradeEnabled()).as("the timed-out toggle wrote nothing").isTrue();
        assertThat(stored.getTotalTrades()).as("the background write completed").isEqualTo(1);
        assertThat(cacheOf(serverA).get(xId).getTotalTrades()).as("the cache follows the write order").isEqualTo(1);

        assertThat(serverA.toggle(x).getWrite()).as("once the write is done, the toggle goes through")
                .isEqualTo(TradeLogService.SettingsWrite.WRITTEN);
        assertThat(storedRow(xId).isTradeEnabled()).isFalse();
        assertThat(cacheOf(serverA).get(xId).isTradeEnabled()).isFalse();
        assertThat(cacheOf(serverA).get(xId).getTotalTrades()).isEqualTo(1);
    }

    @Test
    @DisplayName("A join while the player's background write is in progress returns within about a tick and does not cache; a later read caches the stored row")
    void joinTimesOutWithoutCaching() throws Exception {
        UUID xId = UUID.randomUUID();
        Player x = UltiTradeTestHelper.createMockPlayer("X", xId);
        TradeLogService serverA = server();
        serverA.toggleTrade(x);
        serverA.toggleTrade(x); // a row exists
        doReturn(x).when(Bukkit.getServer()).getPlayer(xId);
        CountDownLatch release = new CountDownLatch(1);
        Thread statistics = holdWhileWriting(serverA, x, release);

        long started = System.nanoTime();
        serverA.playerJoined(x);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        boolean cachedDuringWrite = cacheOf(serverA).containsKey(xId);
        release.countDown();
        statistics.join();

        assertThat(elapsedMillis).as("the join waited about one tick").isLessThan(1000);
        assertThat(cachedDuringWrite).as("the timed-out join did not cache").isFalse();
        assertThat(serverA.getSettings(xId).getTotalTrades()).as("a later read sees the write").isEqualTo(1);
        assertThat(cacheOf(serverA).get(xId).getTotalTrades()).as("and caches the stored row").isEqualTo(1);
    }

    @Test
    @DisplayName("Lock entries do not outlive their use: none is left after writes, a quit during a write, and a second background write that waited")
    void lockEntriesDoNotLeak() throws Exception {
        UUID xId = UUID.randomUUID();
        Player x = UltiTradeTestHelper.createMockPlayer("X", xId);
        TradeLogService serverA = server();
        serverA.toggleTrade(x);
        serverA.toggleTrade(x);
        doReturn(x).when(Bukkit.getServer()).getPlayer(xId);
        serverA.playerJoined(x);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = holdWhileWriting(serverA, x, release);
        serverA.playerQuit(xId); // the player leaves while the write is in progress
        Player partner = UltiTradeTestHelper.createMockPlayer("Partner", UUID.randomUUID());
        Thread second = new Thread(() -> serverA.logCompletedTrade(new TradeSession(x, partner), x, partner, 0.0, 0));
        second.start();
        second.join(300);
        boolean secondWaited = second.isAlive();
        release.countDown();
        first.join();
        second.join();

        assertThat(secondWaited).as("the second write waited for the first: one lock for the player despite the quit").isTrue();
        assertThat(storedRow(xId).getTotalTrades()).as("both writes counted").isEqualTo(2);
        Map<UUID, ?> locks = UltiTradeTestHelper.getField(serverA, "writeLocks");
        assertThat(locks).as("no lock entry is left").isEmpty();
    }
}
