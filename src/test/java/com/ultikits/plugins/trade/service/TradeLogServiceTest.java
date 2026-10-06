package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.entity.TradeLogData;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TradeLogService Tests")
class TradeLogServiceTest {

    private TradeLogService service;
    private TradeConfig config;
    @SuppressWarnings("unchecked")
    private DataOperator<TradeLogData> logOperator = mock(DataOperator.class);
    @SuppressWarnings("unchecked")
    private DataOperator<PlayerTradeSettings> settingsOperator = mock(DataOperator.class);
    @SuppressWarnings("unchecked")
    private Query<PlayerTradeSettings> queryBuilder = mock(Query.class);

    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();

        config = UltiTradeTestHelper.createDefaultConfig();

        service = new TradeLogService();

        // Inject dependencies via reflection
        UltiTradeTestHelper.setField(service, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logOperator", logOperator);
        UltiTradeTestHelper.setField(service, "settingsOperator", settingsOperator);
        // A settings write lands on its stored row unless a test says otherwise. An unstubbed int method
        // on a Mockito mock answers 0, which UltiKits/UltiTrade#52 treats as a write that matched no row.
        lenient().when(settingsOperator.updateCounted(any())).thenReturn(1);

        playerUuid = UUID.randomUUID();
        player = UltiTradeTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** The stored rows behind {@link #storeHolds}: reads return copies, writes land here. */
    private final List<PlayerTradeSettings> store = new ArrayList<>();

    /**
     * Back {@code settingsOperator} with an in-memory table holding {@code rows}: every read returns
     * fresh copies (as a database read does), an insert adds a copy, and {@code updateIf} replaces the row
     * with the same id. A change can then be checked in the store, never in an object the test still holds
     * (UltiKits/UltiTrade#54: the service re-reads and writes conditionally; decision 2026-10-06 00:04).
     */
    private void storeHolds(PlayerTradeSettings... rows) {
        store.clear();
        for (PlayerTradeSettings row : rows) {
            store.add(copy(row));
        }
        lenient().when(settingsOperator.query()).thenReturn(queryBuilder);
        lenient().when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        lenient().when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        lenient().when(queryBuilder.list()).thenAnswer(inv -> {
            List<PlayerTradeSettings> copies = new ArrayList<>();
            for (PlayerTradeSettings row : store) {
                copies.add(copy(row));
            }
            return copies;
        });
        lenient().doAnswer(inv -> {
            store.add(copy(inv.getArgument(0)));
            return null;
        }).when(settingsOperator).insert(any(PlayerTradeSettings.class));
        lenient().when(settingsOperator.updateIf(any(), any(com.ultikits.ultitools.entities.WhereCondition[].class)))
                .thenAnswer(inv -> {
                    PlayerTradeSettings written = inv.getArgument(0);
                    store.removeIf(row -> Objects.equals(row.getId(), written.getId()));
                    store.add(copy(written));
                    return true;
                });
    }

    private static PlayerTradeSettings copy(PlayerTradeSettings row) {
        PlayerTradeSettings copy = new PlayerTradeSettings();
        copy.setId(row.getId());
        copy.setPlayerUuid(row.getPlayerUuid());
        copy.setPlayerName(row.getPlayerName());
        copy.setTradeEnabled(row.isTradeEnabled());
        copy.setBlockedPlayersJson(row.getBlockedPlayersJson());
        copy.setTotalTrades(row.getTotalTrades());
        copy.setTotalMoneyTraded(row.getTotalMoneyTraded());
        copy.setTotalExpTraded(row.getTotalExpTraded());
        copy.setLastTradeTime(row.getLastTradeTime());
        return copy;
    }

    /** The one row the store holds for the test player. */
    private PlayerTradeSettings storedRow() {
        assertThat(store).hasSize(1);
        return store.get(0);
    }

    /**
     * The stored row is gone and cannot be re-created either: no row for the player turns up, and the
     * re-creating insert reaches no row (the JSON backend ignores an insert whose id it already holds), so
     * every write still matches nothing (UltiKits/UltiTrade#52 after UltiKits/UltiTrade#57).
     */
    private void noRowCanBeRecreated() {
        lenient().when(settingsOperator.query()).thenReturn(queryBuilder);
        lenient().when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        lenient().when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        lenient().when(queryBuilder.list()).thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("Player Settings Management")
    class PlayerSettingsManagement {

        @Test
        @DisplayName("getOrCreateSettings should return existing settings")
        void getExistingSettings() {
            PlayerTradeSettings existing = new PlayerTradeSettings(playerUuid, "TestPlayer");
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(existing));

            PlayerTradeSettings result = service.getOrCreateSettings(playerUuid, "TestPlayer");

            assertThat(result).isSameAs(existing);
            verify(settingsOperator, never()).insert(any());
        }

        @Test
        @DisplayName("getOrCreateSettings should select canonical lowest-id settings when duplicate rows exist")
        void getOrCreateSettingsDuplicateRowsSelectLowestId() {
            PlayerTradeSettings duplicate = new PlayerTradeSettings(playerUuid, "Duplicate");
            duplicate.setId("settings-200");
            PlayerTradeSettings canonical = new PlayerTradeSettings(playerUuid, "TestPlayer");
            canonical.setId("settings-100");
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Arrays.asList(duplicate, canonical));

            PlayerTradeSettings result = service.getOrCreateSettings(playerUuid, "TestPlayer");

            assertThat(result).isSameAs(canonical);
            verify(settingsOperator, never()).insert(any());
        }

        @Test
        @DisplayName("getOrCreateSettings should create new settings if not found")
        void createNewSettings() {
            storeHolds(); // an empty table that keeps what is inserted: the new row is read back (Codex run 1 on PR #66)

            PlayerTradeSettings result = service.getOrCreateSettings(playerUuid, "TestPlayer");

            assertThat(result).isNotNull();
            assertThat(result.getPlayerUuid()).isEqualTo(playerUuid.toString());
            assertThat(result.getPlayerName()).isEqualTo("TestPlayer");
            verify(settingsOperator).insert(any(PlayerTradeSettings.class));
        }

        @Test
        @DisplayName("getOrCreateSettings should return cached settings")
        void getCachedSettings() throws Exception {
            PlayerTradeSettings cached = new PlayerTradeSettings(playerUuid, "TestPlayer");

            Map<UUID, PlayerTradeSettings> cache = UltiTradeTestHelper.getField(service, "settingsCache");
            cache.put(playerUuid, cached);

            PlayerTradeSettings result = service.getOrCreateSettings(playerUuid, "TestPlayer");

            assertThat(result).isSameAs(cached);
            verify(settingsOperator, never()).query();
        }

        @Test
        @DisplayName("getOrCreateSettings should update name if changed")
        void updateNameIfChanged() {
            PlayerTradeSettings existing = new PlayerTradeSettings(playerUuid, "OldName");
            existing.setId(playerUuid.toString());
            storeHolds(existing);

            PlayerTradeSettings result = service.getOrCreateSettings(playerUuid, "NewName");

            assertThat(result.getPlayerName()).isEqualTo("NewName");
            assertThat(storedRow().getPlayerName()).as("the new name is written, not only returned").isEqualTo("NewName");
        }

        @Test
        @DisplayName("getOrCreateSettings should not update name if same")
        void dontUpdateNameIfSame() {
            PlayerTradeSettings existing = new PlayerTradeSettings(playerUuid, "TestPlayer");
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(existing));

            service.getOrCreateSettings(playerUuid, "TestPlayer");

            // The name is the same, so nothing is written
            assertThat(existing.getPlayerName()).isEqualTo("TestPlayer");
            verify(settingsOperator, never()).updateIf(any(), any(com.ultikits.ultitools.entities.WhereCondition[].class));
        }

        @Test
        @DisplayName("getOrCreateSettings should handle null result from getAll")
        void handleNullResult() {
            storeHolds();
            // The first read answers null; later reads see the table, so the inserted row is read back (Codex run 1)
            lenient().when(queryBuilder.list()).thenReturn(null).thenAnswer(inv -> {
                List<PlayerTradeSettings> copies = new ArrayList<>();
                for (PlayerTradeSettings row : store) {
                    copies.add(copy(row));
                }
                return copies;
            });

            PlayerTradeSettings result = service.getOrCreateSettings(playerUuid, "TestPlayer");

            assertThat(result).isNotNull();
            verify(settingsOperator).insert(any(PlayerTradeSettings.class));
        }

        @Test
        @DisplayName("getSettings should return null if not found")
        void getSettingsNotFound() {
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.emptyList());

            PlayerTradeSettings result = service.getSettings(playerUuid);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("getSettings should return existing settings")
        void getSettingsFound() {
            PlayerTradeSettings existing = new PlayerTradeSettings(playerUuid, "TestPlayer");
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(existing));

            PlayerTradeSettings result = service.getSettings(playerUuid);

            assertThat(result).isSameAs(existing);
        }

        @Test
        @DisplayName("getSettings should select canonical lowest-id settings when duplicate rows exist")
        void getSettingsDuplicateRowsSelectLowestId() {
            PlayerTradeSettings duplicate = new PlayerTradeSettings(playerUuid, "Duplicate");
            duplicate.setId("settings-200");
            PlayerTradeSettings canonical = new PlayerTradeSettings(playerUuid, "TestPlayer");
            canonical.setId("settings-100");
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Arrays.asList(duplicate, canonical));

            PlayerTradeSettings result = service.getSettings(playerUuid);

            assertThat(result).isSameAs(canonical);
        }

        @Test
        @DisplayName("getSettings should return cached settings without DB query")
        void getSettingsCached() throws Exception {
            PlayerTradeSettings cached = new PlayerTradeSettings(playerUuid, "TestPlayer");
            Map<UUID, PlayerTradeSettings> cache = UltiTradeTestHelper.getField(service, "settingsCache");
            cache.put(playerUuid, cached);

            PlayerTradeSettings result = service.getSettings(playerUuid);

            assertThat(result).isSameAs(cached);
            verify(settingsOperator, never()).query();
        }

        @Test
        @DisplayName("getSettings should cache results from DB for a player online on this server (UltiTrade#54: the cache is scoped to the player's time here)")
        void getSettingsCachesResult() throws Exception {
            org.mockito.Mockito.doReturn(player).when(org.bukkit.Bukkit.getServer()).getPlayer(playerUuid);
            PlayerTradeSettings existing = new PlayerTradeSettings(playerUuid, "TestPlayer");
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(existing));

            service.getSettings(playerUuid);

            Map<UUID, PlayerTradeSettings> cache = UltiTradeTestHelper.getField(service, "settingsCache");
            assertThat(cache).containsKey(playerUuid);
        }

        @Test
        @DisplayName("getSettings should handle null result from getAll")
        void getSettingsNullResult() {
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(null);

            PlayerTradeSettings result = service.getSettings(playerUuid);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Trade Toggle")
    class TradeToggle {

        @Test
        @DisplayName("isTradeEnabled should return true for non-existent settings")
        void isTradeEnabledDefault() {
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.emptyList());

            boolean result = service.isTradeEnabled(playerUuid);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isTradeEnabled should return settings value")
        void isTradeEnabledFromSettings() {
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.setTradeEnabled(false);
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(settings));

            boolean result = service.isTradeEnabled(playerUuid);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("toggleTrade should toggle and return new state")
        void toggleTrade() {
            storeHolds();

            boolean result = service.toggleTrade(player);

            assertThat(result).isFalse(); // Was true, now false
            assertThat(storedRow().isTradeEnabled()).isFalse();
        }

        @Test
        @DisplayName("toggleTrade should toggle back to true")
        void toggleTradeBack() {
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.setId(playerUuid.toString());
            settings.setTradeEnabled(false);
            storeHolds(settings);

            boolean result = service.toggleTrade(player);

            assertThat(result).isTrue(); // Was false, now true
            assertThat(storedRow().isTradeEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("Block Management")
    class BlockManagement {

        @Test
        @DisplayName("isBlocked should return false for non-existent settings")
        void isBlockedDefault() {
            UUID targetUuid = UUID.randomUUID();
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.emptyList());

            boolean result = service.isBlocked(playerUuid, targetUuid);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("isBlocked should check settings")
        void isBlockedFromSettings() {
            UUID targetUuid = UUID.randomUUID();
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.blockPlayer(targetUuid.toString());
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(settings));

            boolean result = service.isBlocked(playerUuid, targetUuid);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("blockPlayer should add to blocked list")
        void blockPlayer() {
            UUID targetUuid = UUID.randomUUID();
            storeHolds();

            boolean result = service.blockPlayer(player, targetUuid);

            assertThat(result).isTrue();
            assertThat(storedRow().isBlocked(targetUuid.toString())).isTrue();
        }

        @Test
        @DisplayName("blockPlayer should return false for duplicate")
        void blockPlayerDuplicate() throws Exception {
            UUID targetUuid = UUID.randomUUID();
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.blockPlayer(targetUuid.toString());
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(settings));

            Map<UUID, PlayerTradeSettings> cache = UltiTradeTestHelper.getField(service, "settingsCache");
            cache.put(playerUuid, settings);
            boolean result = service.blockPlayer(player, targetUuid);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("blockPlayer should not save when already blocked")
        void blockPlayerNoSaveOnDuplicate() throws Exception {
            UUID targetUuid = UUID.randomUUID();
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.blockPlayer(targetUuid.toString());
            settings.setId(playerUuid.toString());
            storeHolds(settings);

            assertThat(service.block(player, targetUuid)).isEqualTo(TradeLogService.SettingsWrite.UNCHANGED);

            // the stored list already holds the target: nothing is written
            verify(settingsOperator, never()).updateIf(any(), any(com.ultikits.ultitools.entities.WhereCondition[].class));
            verify(settingsOperator, never()).insert(any(PlayerTradeSettings.class));
        }

        @Test
        @DisplayName("unblockPlayer should remove from blocked list")
        void unblockPlayer() throws Exception {
            UUID targetUuid = UUID.randomUUID();
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.blockPlayer(targetUuid.toString());
            settings.setId(playerUuid.toString());
            storeHolds(settings);

            boolean result = service.unblockPlayer(player, targetUuid);

            assertThat(result).isTrue();
            assertThat(storedRow().isBlocked(targetUuid.toString())).isFalse();
        }

        @Test
        @DisplayName("unblockPlayer should return false for non-blocked")
        void unblockPlayerNotBlocked() {
            UUID targetUuid = UUID.randomUUID();
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.emptyList());

            boolean result = service.unblockPlayer(player, targetUuid);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Player Statistics")
    class PlayerStatistics {

        @Test
        @DisplayName("getPlayerStats should return settings if found")
        void getPlayerStatsFound() {
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.setTotalTrades(10);
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.singletonList(settings));

            PlayerTradeSettings result = service.getPlayerStats(playerUuid);

            assertThat(result).isSameAs(settings);
            assertThat(result.getTotalTrades()).isEqualTo(10);
        }

        @Test
        @DisplayName("getPlayerStats should return default if not found")
        void getPlayerStatsNotFound() {
            when(settingsOperator.query()).thenReturn(queryBuilder);
        when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
        when(queryBuilder.eq(any())).thenReturn(queryBuilder);
        when(queryBuilder.list())
                    .thenReturn(Collections.emptyList());

            PlayerTradeSettings result = service.getPlayerStats(playerUuid);

            assertThat(result).isNotNull();
            assertThat(result.getPlayerUuid()).isEqualTo(playerUuid.toString());
            assertThat(result.getTotalTrades()).isZero();
        }
    }

    @Nested
    @DisplayName("Trade Logs Retrieval")
    class TradeLogsRetrieval {

        @Test
        @DisplayName("getPlayerLogs should filter by player UUID")
        void getPlayerLogsFiltered() {
            UUID otherUuid = UUID.randomUUID();

            TradeLogData log1 = new TradeLogData(UUID.randomUUID(), playerUuid, "TestPlayer", otherUuid, "Other");
            log1.setTradeTime(1000L);

            TradeLogData log2 = new TradeLogData(UUID.randomUUID(), otherUuid, "Other", UUID.randomUUID(), "Third");
            log2.setTradeTime(2000L);

            TradeLogData log3 = new TradeLogData(UUID.randomUUID(), UUID.randomUUID(), "Third", playerUuid, "TestPlayer");
            log3.setTradeTime(3000L);

            when(logOperator.getAll()).thenReturn(Arrays.asList(log1, log2, log3));

            List<TradeLogData> result = service.getPlayerLogs(playerUuid, 10);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isSameAs(log3); // Most recent first
            assertThat(result.get(1)).isSameAs(log1);
        }

        @Test
        @DisplayName("getPlayerLogs should sort by time descending")
        void getPlayerLogsSorted() {
            TradeLogData older = new TradeLogData(UUID.randomUUID(), playerUuid, "TestPlayer", UUID.randomUUID(), "Other");
            older.setTradeTime(1000L);

            TradeLogData newer = new TradeLogData(UUID.randomUUID(), playerUuid, "TestPlayer", UUID.randomUUID(), "Other");
            newer.setTradeTime(2000L);

            when(logOperator.getAll()).thenReturn(Arrays.asList(older, newer));

            List<TradeLogData> result = service.getPlayerLogs(playerUuid, 10);

            assertThat(result).extracting(TradeLogData::getTradeTime)
                    .containsExactly(2000L, 1000L);
        }

        @Test
        @DisplayName("getPlayerLogs should limit results")
        void getPlayerLogsLimited() {
            List<TradeLogData> logs = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                TradeLogData log = new TradeLogData(UUID.randomUUID(), playerUuid, "TestPlayer", UUID.randomUUID(), "Other");
                log.setTradeTime(i * 1000L);
                logs.add(log);
            }

            when(logOperator.getAll()).thenReturn(logs);

            List<TradeLogData> result = service.getPlayerLogs(playerUuid, 5);

            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("getPlayerLogs should return empty list on error")
        void getPlayerLogsError() {
            when(logOperator.getAll()).thenThrow(new RuntimeException("Database error"));

            List<TradeLogData> result = service.getPlayerLogs(playerUuid, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getPlayerLogs should return all when under limit")
        void getPlayerLogsUnderLimit() {
            TradeLogData log1 = new TradeLogData(UUID.randomUUID(), playerUuid, "TestPlayer", UUID.randomUUID(), "Other");
            log1.setTradeTime(1000L);

            when(logOperator.getAll()).thenReturn(Collections.singletonList(log1));

            List<TradeLogData> result = service.getPlayerLogs(playerUuid, 10);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getPlayerLogs should return empty when no matching logs")
        void getPlayerLogsNoMatch() {
            UUID otherUuid = UUID.randomUUID();
            TradeLogData log = new TradeLogData(UUID.randomUUID(), otherUuid, "Other", UUID.randomUUID(), "Third");
            log.setTradeTime(1000L);

            when(logOperator.getAll()).thenReturn(Collections.singletonList(log));

            List<TradeLogData> result = service.getPlayerLogs(playerUuid, 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Shutdown")
    class Shutdown {

        @Test
        @DisplayName("shutdown should cancel cleanup task")
        void cancelCleanupTask() throws Exception {
            BukkitTask mockTask = mock(BukkitTask.class);
            UltiTradeTestHelper.setField(service, "cleanupTask", mockTask);

            service.shutdown();

            verify(mockTask).cancel();
        }

        @Test
        @DisplayName("shutdown should handle null cleanup task")
        void nullCleanupTask() {
            // Should not throw
            service.shutdown();
        }

        @Test
        @DisplayName("shutdown writes no cached settings: every change was written when made, and a cached copy would revert another server's change (UltiKits/UltiTrade#54, decision 2026-10-06 00:04)")
        void shutdownWritesNoCachedSettings() throws Exception {
            Map<UUID, PlayerTradeSettings> cache = UltiTradeTestHelper.getField(service, "settingsCache");
            PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, "TestPlayer");
            settings.setTradeEnabled(false);
            cache.put(playerUuid, settings);
            PlayerTradeSettings gone = new PlayerTradeSettings(UUID.randomUUID(), "Other");
            gone.setId("row-deleted-while-running");
            cache.put(UUID.fromString(gone.getPlayerUuid()), gone);

            service.shutdown();

            verifyNoInteractions(settingsOperator);
            verify(UltiTradeTestHelper.getMockLogger(), never()).warn(anyString());
            verify(UltiTradeTestHelper.getMockLogger(), never()).warn(any(Throwable.class), anyString());
            assertThat(cache).isEmpty();
        }

        @Test
        @DisplayName("shutdown should clear the cache")
        void clearCacheAfterSaving() throws Exception {
            Map<UUID, PlayerTradeSettings> cache = UltiTradeTestHelper.getField(service, "settingsCache");
            cache.put(playerUuid, new PlayerTradeSettings(playerUuid, "TestPlayer"));

            service.shutdown();

            assertThat(cache).isEmpty();
        }

        @Test
        @DisplayName("shutdown should set cleanup task to null")
        void setCleanupTaskNull() throws Exception {
            BukkitTask mockTask = mock(BukkitTask.class);
            UltiTradeTestHelper.setField(service, "cleanupTask", mockTask);

            service.shutdown();

            BukkitTask taskAfter = UltiTradeTestHelper.getField(service, "cleanupTask");
            assertThat(taskAfter).isNull();
        }
    }

    @Nested
    @DisplayName("Logging (disabled)")
    class LoggingDisabled {

        @Test
        @DisplayName("logCompletedTrade should do nothing when logging disabled")
        void logCompletedTradeDisabled() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(false);

            com.ultikits.plugins.trade.entity.TradeSession session = mock(com.ultikits.plugins.trade.entity.TradeSession.class);

            service.logCompletedTrade(session, player, player, 0, 0);

            // No interaction with scheduler since logging is disabled
            verify(org.bukkit.Bukkit.getServer().getScheduler(), never())
                    .runTaskAsynchronously(any(), any(Runnable.class));
        }

        @Test
        @DisplayName("logCancelledTrade should do nothing when logging disabled")
        void logCancelledTradeDisabled() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(false);

            com.ultikits.plugins.trade.entity.TradeSession session = mock(com.ultikits.plugins.trade.entity.TradeSession.class);

            service.logCancelledTrade(session, "test reason");

            verify(org.bukkit.Bukkit.getServer().getScheduler(), never())
                    .runTaskAsynchronously(any(), any(Runnable.class));
        }
    }

    @Nested
    @DisplayName("Logging (enabled)")
    class LoggingEnabled {

        @Test
        @DisplayName("logCompletedTrade should schedule async task when enabled")
        void logCompletedTradeEnabled() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            UltiTradeTestHelper.setField(service, "bukkitPlugin", org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools"));

            com.ultikits.plugins.trade.entity.TradeSession session = new com.ultikits.plugins.trade.entity.TradeSession(player, player);

            UUID otherUuid = UUID.randomUUID();
            Player other = UltiTradeTestHelper.createMockPlayer("OtherPlayer", otherUuid);

            service.logCompletedTrade(session, player, other, 5.0, 10);

            verify(org.bukkit.Bukkit.getServer().getScheduler())
                    .runTaskAsynchronously(any(), any(Runnable.class));
        }

        @Test
        @DisplayName("logCancelledTrade should schedule async task when enabled")
        void logCancelledTradeEnabled() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            UltiTradeTestHelper.setField(service, "bukkitPlugin", org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools"));

            com.ultikits.plugins.trade.entity.TradeSession session = new com.ultikits.plugins.trade.entity.TradeSession(player, player);

            service.logCancelledTrade(session, "test reason");

            verify(org.bukkit.Bukkit.getServer().getScheduler())
                    .runTaskAsynchronously(any(), any(Runnable.class));
        }

        @Test
        @DisplayName("logCompletedTrade async task should insert log and update stats")
        void logCompletedTradeAsync() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            UltiTradeTestHelper.setField(service, "bukkitPlugin", org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools"));

            com.ultikits.plugins.trade.entity.TradeSession session = new com.ultikits.plugins.trade.entity.TradeSession(player, player);
            session.setMoney(playerUuid, 100.0);
            session.setExp(playerUuid, 50);

            UUID otherUuid = UUID.randomUUID();
            Player other = UltiTradeTestHelper.createMockPlayer("OtherPlayer", otherUuid);

            // Capture the Runnable
            org.bukkit.scheduler.BukkitScheduler scheduler = org.bukkit.Bukkit.getServer().getScheduler();
            org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

            service.logCompletedTrade(session, player, other, 5.0, 10);

            verify(scheduler).runTaskAsynchronously(any(), captor.capture());

            // Mock the query chain for updatePlayerStats -> getOrCreateSettings
            when(settingsOperator.query()).thenReturn(queryBuilder);
            when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
            when(queryBuilder.eq(any())).thenReturn(queryBuilder);
            when(queryBuilder.list()).thenReturn(Collections.emptyList());

            // Run the async task
            captor.getValue().run();

            // Should insert a log
            verify(logOperator).insert(any(TradeLogData.class));
        }

        @Test
        @DisplayName("logCancelledTrade async task should insert cancelled log")
        void logCancelledTradeAsync() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            UltiTradeTestHelper.setField(service, "bukkitPlugin", org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools"));

            com.ultikits.plugins.trade.entity.TradeSession session = new com.ultikits.plugins.trade.entity.TradeSession(player, player);

            org.bukkit.Server server = org.bukkit.Bukkit.getServer();
            when(server.getPlayer(any(UUID.class))).thenReturn(player);

            org.bukkit.scheduler.BukkitScheduler scheduler = server.getScheduler();
            org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

            service.logCancelledTrade(session, "Player left");

            verify(scheduler).runTaskAsynchronously(any(), captor.capture());

            // Run the async task
            captor.getValue().run();

            // Should insert a log
            verify(logOperator).insert(any(TradeLogData.class));
        }

        @Test
        @DisplayName("logCompletedTrade should handle exception in async task")
        void logCompletedTradeException() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            UltiTradeTestHelper.setField(service, "bukkitPlugin", org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools"));

            com.ultikits.plugins.trade.entity.TradeSession session = new com.ultikits.plugins.trade.entity.TradeSession(player, player);
            UUID otherUuid = UUID.randomUUID();
            Player other = UltiTradeTestHelper.createMockPlayer("OtherPlayer", otherUuid);

            org.bukkit.scheduler.BukkitScheduler scheduler = org.bukkit.Bukkit.getServer().getScheduler();
            org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

            doThrow(new RuntimeException("DB error")).when(logOperator).insert(any());

            service.logCompletedTrade(session, player, other, 0, 0);

            verify(scheduler).runTaskAsynchronously(any(), captor.capture());

            // Should not throw
            captor.getValue().run();
        }

        @Test
        @DisplayName("logCancelledTrade should handle exception in async task")
        void logCancelledTradeException() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            UltiTradeTestHelper.setField(service, "bukkitPlugin", org.bukkit.Bukkit.getPluginManager().getPlugin("UltiTools"));

            com.ultikits.plugins.trade.entity.TradeSession session = new com.ultikits.plugins.trade.entity.TradeSession(player, player);
            org.bukkit.Server server = org.bukkit.Bukkit.getServer();
            when(server.getPlayer(any(UUID.class))).thenReturn(player);

            org.bukkit.scheduler.BukkitScheduler scheduler = server.getScheduler();
            org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

            doThrow(new RuntimeException("DB error")).when(logOperator).insert(any());

            service.logCancelledTrade(session, "test");

            verify(scheduler).runTaskAsynchronously(any(), captor.capture());

            // Should not throw
            captor.getValue().run();
        }
    }

    @Nested
    @DisplayName("Settings writes: a write that reaches no row is reported, never passed as saved (UltiKits/UltiTrade#52, through #54's conditional write)")
    class SaveSettings {

        // The whole-object saveSettings these tests exercised was removed with UltiKits/UltiTrade#54
        // (decision 2026-10-06 00:04): every change now re-reads the row and writes conditionally. #52's
        // claim is unchanged and is pinned here on the new path.

        @Test
        @DisplayName("a toggle whose stored row is gone and cannot be re-created is logged as not saved, naming the player, and reported FAILED")
        void toggleOfAMissingRowIsLoggedAsFailed() throws Exception {
            noRowCanBeRecreated();

            TradeLogService.SettingsChangeResult result = service.toggle(player);

            assertThat(result.getWrite()).isEqualTo(TradeLogService.SettingsWrite.FAILED);
            verify(UltiTradeTestHelper.getMockLogger()).warn(any(Throwable.class),
                    eq(zhLine("log_settings_save_failed").replace("{PLAYER}", "TestPlayer")));
            verify(settingsOperator, never()).update(any(PlayerTradeSettings.class));
        }

        @Test
        @DisplayName("a post-trade statistics update whose stored row is gone and cannot be re-created is logged as not saved, on the inline path")
        void statsOfAMissingRowIsLoggedAsFailedInline() throws Exception {
            // No enabled plugin to schedule through, so the write runs on the calling thread.
            noRowCanBeRecreated();
            Player partner = UltiTradeTestHelper.createMockPlayer("Partner", UUID.randomUUID());
            com.ultikits.plugins.trade.entity.TradeSession session =
                    new com.ultikits.plugins.trade.entity.TradeSession(player, partner);

            service.logCompletedTrade(session, player, partner, 0.0, 0);

            verify(UltiTradeTestHelper.getMockLogger()).warn(any(Throwable.class),
                    eq(zhLine("log_settings_save_failed").replace("{PLAYER}", "TestPlayer")));
        }

        @Test
        @DisplayName("control: a toggle that wrote its row logs nothing")
        void toggleThatWroteItsRowLogsNothing() throws Exception {
            PlayerTradeSettings stored = new PlayerTradeSettings(playerUuid, "TestPlayer");
            stored.setId(playerUuid.toString());
            lenient().when(settingsOperator.query()).thenReturn(queryBuilder);
            lenient().when(queryBuilder.where(anyString())).thenReturn(queryBuilder);
            lenient().when(queryBuilder.eq(any())).thenReturn(queryBuilder);
            lenient().when(queryBuilder.list()).thenReturn(Collections.singletonList(stored));
            when(settingsOperator.updateIf(any(), any(com.ultikits.ultitools.entities.WhereCondition[].class))).thenReturn(true);

            TradeLogService.SettingsChangeResult result = service.toggle(player);

            assertThat(result.getWrite()).isEqualTo(TradeLogService.SettingsWrite.WRITTEN);
            assertThat(result.getSettings().isTradeEnabled()).isFalse();
            verify(UltiTradeTestHelper.getMockLogger(), never()).warn(anyString());
            verify(UltiTradeTestHelper.getMockLogger(), never()).warn(any(Throwable.class), anyString());
        }
    }

    @Nested
    @DisplayName("Init and Cleanup Scheduling")
    class InitAndCleanupScheduling {

        @Test
        @DisplayName("init should wire the data operators and schedule cleanup when trade logging is enabled")
        void initSchedulesCleanupWhenEnabled() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            when(config.getCleanupIntervalHours()).thenReturn(24);

            service.init();

            verify(UltiTradeTestHelper.getMockPlugin()).getDataOperator(TradeLogData.class);
            verify(UltiTradeTestHelper.getMockPlugin()).getDataOperator(PlayerTradeSettings.class);

            long expectedTicks = 24L * 60L * 60L * 20L;
            verify(org.bukkit.Bukkit.getServer().getScheduler()).runTaskTimerAsynchronously(
                    any(), any(Runnable.class), eq(expectedTicks), eq(expectedTicks));
        }

        @Test
        @DisplayName("init should skip cleanup scheduling entirely when trade logging is disabled")
        void initSkipsCleanupWhenDisabled() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(false);

            service.init();

            verify(org.bukkit.Bukkit.getServer().getScheduler(), never())
                    .runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("Cleanup Old Logs")
    class CleanupOldLogs {

        /**
         * Captures the runnable init() schedules for periodic cleanup and returns it,
         * without actually running it yet.
         */
        private Runnable scheduleAndCaptureCleanupTask() throws Exception {
            when(config.isEnableTradeLog()).thenReturn(true);
            // init() re-wires logOperator/settingsOperator from plugin.getDataOperator(...),
            // which would otherwise replace this test's own logOperator mock with a fresh one
            // that verify() calls below can never see.
            when(UltiTradeTestHelper.getMockPlugin().getDataOperator(TradeLogData.class))
                    .thenReturn(logOperator);

            org.bukkit.scheduler.BukkitScheduler scheduler = org.bukkit.Bukkit.getServer().getScheduler();
            org.mockito.ArgumentCaptor<Runnable> captor = org.mockito.ArgumentCaptor.forClass(Runnable.class);

            service.init();

            verify(scheduler).runTaskTimerAsynchronously(any(), captor.capture(), anyLong(), anyLong());
            return captor.getValue();
        }

        @Test
        @DisplayName("cleanupOldLogs should delete only logs older than the retention window and report the count")
        void deletesExpiredLogsOnly() throws Exception {
            when(config.getLogRetentionDays()).thenReturn(30);
            Runnable cleanupTask = scheduleAndCaptureCleanupTask();

            TradeLogData expired = mock(TradeLogData.class);
            when(expired.getTradeTime()).thenReturn(System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000);
            when(expired.getId()).thenReturn("expired-log");

            TradeLogData fresh = mock(TradeLogData.class);
            when(fresh.getTradeTime()).thenReturn(System.currentTimeMillis());
            when(fresh.getId()).thenReturn("fresh-log");

            when(logOperator.getAll()).thenReturn(Arrays.asList(expired, fresh));

            cleanupTask.run();

            verify(logOperator).delById("expired-log");
            verify(logOperator, never()).delById("fresh-log");
            verify(UltiTradeTestHelper.getMockLogger()).info(contains("1"));
        }

        @Test
        @DisplayName("cleanupOldLogs should not log anything when no logs are expired")
        void reportsNothingWhenNoneExpired() throws Exception {
            when(config.getLogRetentionDays()).thenReturn(30);
            Runnable cleanupTask = scheduleAndCaptureCleanupTask();

            TradeLogData fresh = mock(TradeLogData.class);
            when(fresh.getTradeTime()).thenReturn(System.currentTimeMillis());

            when(logOperator.getAll()).thenReturn(Collections.singletonList(fresh));

            cleanupTask.run();

            verify(logOperator, never()).delById(any());
            verify(UltiTradeTestHelper.getMockLogger(), never()).info(anyString());
        }

        @Test
        @DisplayName("cleanupOldLogs should swallow a data-layer exception without propagating it")
        void swallowsDataLayerException() throws Exception {
            when(config.getLogRetentionDays()).thenReturn(30);
            Runnable cleanupTask = scheduleAndCaptureCleanupTask();

            when(logOperator.getAll()).thenThrow(new RuntimeException("DB unavailable"));

            // Should not throw
            cleanupTask.run();

            verify(UltiTradeTestHelper.getMockLogger()).warn(any(Exception.class), anyString());
        }
    }

    /** The Chinese catalogue's console line for {@code key}, or a marker naming the missing key. */
    private static String zhLine(String key) {
        return com.ultikits.plugins.trade.i18n.CatalogueText.entries("zh")
                .getOrDefault(key, "<lang/zh has no " + key + ">");
    }
}
