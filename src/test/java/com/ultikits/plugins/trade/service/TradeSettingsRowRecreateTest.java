package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.testsupport.SharedSqliteDatabase;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A player's trade-settings row deleted while the server runs
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/57">UltiTrade#57</a>).
 *
 * <h2>The defect</h2>
 * The module keeps each player's settings in memory for the server's lifetime. Once the row is gone (an
 * administrator, or another server on the same database, deleted it), every later save matches no row:
 * since UltiTrade#52 it is logged as failed, but the player's chat still confirms the change and the
 * change is lost at the next restart.
 *
 * <h2>The decision this implements (maintainer, 2026-10-04)</h2>
 * A save that matches no stored row re-creates the row with the current settings, so the change is kept
 * and the reply stays true. The re-created row must be the one later reads find, and two servers
 * re-creating it at once must not leave two rows.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * Every server is a real {@link TradeLogService} over the framework's real SQLite operator, all on one
 * database file, so the assertions read the table itself and what a server that has nothing cached
 * reads from it -- the state a restart would see.
 */
@DisplayName("A trade-settings save whose row was deleted re-creates the row (UltiTrade#57)")
class TradeSettingsRowRecreateTest {

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
        player = UltiTradeTestHelper.createMockPlayer("Settler", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** One server: its own service and its own operator on the shared file. No plugin to schedule through, so writes run inline. */
    private TradeLogService server() throws Exception {
        TradeLogService service = new TradeLogService();
        UltiTradeTestHelper.setField(service, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(service, "config", UltiTradeTestHelper.createDefaultConfig());
        UltiTradeTestHelper.setField(service, "settingsOperator", database.openAs(PlayerTradeSettings.class));
        return service;
    }

    private List<PlayerTradeSettings> rowsOfPlayer() {
        return table.getAll(WhereCondition.builder().column("player_uuid").value(playerUuid.toString()).build());
    }

    /** What a server that has nothing cached -- one that just restarted -- reads for the player. */
    private PlayerTradeSettings readAfterRestart() throws Exception {
        return server().getSettings(playerUuid);
    }

    private void deleteThePlayersRow() {
        for (PlayerTradeSettings row : rowsOfPlayer()) {
            table.delById(row.getId());
        }
        assertThat(rowsOfPlayer()).as("precondition: the row is gone").isEmpty();
    }

    @Test
    @DisplayName("A toggle after the row was deleted re-creates it with the new state; a restarted server reads it; no failure is logged")
    void toggleRecreatesTheRow() throws Exception {
        TradeLogService serverA = server();
        assertThat(serverA.toggleTrade(player)).as("first toggle: trading off").isFalse();
        deleteThePlayersRow();

        boolean reply = serverA.toggleTrade(player); // back on
        assertThat(reply).isTrue();
        serverA.toggleTrade(player); // and off again: the state the player was last told about

        assertThat(rowsOfPlayer()).as("exactly one row again").hasSize(1);
        PlayerTradeSettings read = readAfterRestart();
        assertThat(read).as("a restarted server finds the row").isNotNull();
        assertThat(read.isTradeEnabled()).as("holding the state the player was told about").isFalse();
        verify(UltiTradeTestHelper.getMockLogger(), never()).warn(UltiTradeTestHelper.getMockPlugin().i18n("log_settings_write_failed"));
    }

    @Test
    @DisplayName("A block after the row was deleted is kept: the re-created row holds the blocklist")
    void blockRecreatesTheRow() throws Exception {
        TradeLogService serverA = server();
        serverA.toggleTrade(player);
        serverA.toggleTrade(player);
        deleteThePlayersRow();
        UUID blocked = UUID.randomUUID();

        assertThat(serverA.blockPlayer(player, blocked)).isTrue();

        PlayerTradeSettings read = readAfterRestart();
        assertThat(read).isNotNull();
        assertThat(read.isBlocked(blocked.toString())).isTrue();
    }

    @Test
    @DisplayName("The shutdown save re-creates a deleted row with the cached settings")
    void shutdownRecreatesTheRow() throws Exception {
        TradeLogService serverA = server();
        serverA.toggleTrade(player); // off
        deleteThePlayersRow();

        serverA.shutdown();

        PlayerTradeSettings read = readAfterRestart();
        assertThat(read).isNotNull();
        assertThat(read.isTradeEnabled()).isFalse();
        assertThat(rowsOfPlayer()).hasSize(1);
    }

    @Test
    @DisplayName("Two servers that both cached the row and both save after it was deleted leave one row, not two")
    void twoServersRecreatingLeaveOneRow() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        serverA.toggleTrade(player); // off; A caches the row
        assertThat(serverB.getSettings(playerUuid)).as("B caches the same row").isNotNull();
        deleteThePlayersRow();

        serverA.shutdown();
        serverB.shutdown();

        assertThat(rowsOfPlayer()).as("one row for the player").hasSize(1);
        assertThat(readAfterRestart()).isNotNull();
    }

    @Test
    @DisplayName("A server re-creating the row after another server created a fresh one writes onto that row instead of adding a second")
    void recreateAfterAnotherServerCreatedAFreshRowLeavesOneRow() throws Exception {
        TradeLogService serverA = server();
        serverA.toggleTrade(player); // A caches the row
        deleteThePlayersRow();
        TradeLogService serverB = server();
        serverB.toggleTrade(player); // B has nothing cached: it creates a fresh row
        assertThat(rowsOfPlayer()).hasSize(1);

        serverA.shutdown(); // A's cached copy matches no row

        assertThat(rowsOfPlayer()).as("still one row for the player").hasSize(1);
        assertThat(readAfterRestart()).isNotNull();
    }

    @Test
    @DisplayName("Two servers each creating the player's first row at once leave one row")
    void twoServersCreatingTheFirstRowLeaveOneRow() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        // B reads "no row", and A creates the row between B's read and B's insert.
        DataOperator<PlayerTradeSettings> bOperator = UltiTradeTestHelper.getField(serverB, "settingsOperator");
        UltiTradeTestHelper.setField(serverB, "settingsOperator",
                beforeInsert(bOperator, () -> serverA.toggleTrade(player)));

        serverB.toggleTrade(player);

        assertThat(rowsOfPlayer()).as("one row for the player").hasSize(1);
        assertThat(readAfterRestart()).isNotNull();
    }

    /** {@code operator}, with {@code hook} run once just before its first insert. */
    @SuppressWarnings("unchecked")
    private static DataOperator<PlayerTradeSettings> beforeInsert(DataOperator<PlayerTradeSettings> operator, Runnable hook) {
        boolean[] ran = {false};
        return (DataOperator<PlayerTradeSettings>) java.lang.reflect.Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    if (method.getName().equals("insert") && !ran[0]) {
                        ran[0] = true;
                        hook.run();
                    }
                    try {
                        return method.invoke(operator, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
