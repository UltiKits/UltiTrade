package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.testsupport.InterleavingOperator;
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

/**
 * Every way a player's trade settings change, against a second server sharing the database
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/54">UltiTrade#54</a>, plan 17-83 Task 2).
 *
 * <h2>The shape of each test</h2>
 * Server A reads the player's settings first (on the base it then kept that copy for its lifetime).
 * Server B's change is made in the window between A's read and A's write: {@link InterleavingOperator}
 * runs it just before A's write reaches the database. After A's change the stored row must hold
 * <b>both</b> changes -- B's is not reverted, and A's own is applied on top of it.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * Each server is a real {@link TradeLogService} with its own operator on one SQLite file; the assertions
 * read the table. Each test also asserts that B's change was really made (the interleaving ran).
 */
@DisplayName("Every trade-settings change keeps a change another server made in between (UltiTrade#54)")
class TradeSettingsChangePathsTest {

    @TempDir
    Path dir;

    private SharedSqliteDatabase database;
    private DataOperator<PlayerTradeSettings> table;
    private UUID playerUuid;
    private Player player;
    private boolean otherServerChanged;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(PlayerTradeSettings.class);
        playerUuid = UUID.randomUUID();
        player = UltiTradeTestHelper.createMockPlayer("Mover", playerUuid);
        otherServerChanged = false;
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** One server: its own service and its own operator on the shared file. Writes run inline. */
    private TradeLogService server() throws Exception {
        TradeLogService service = new TradeLogService();
        UltiTradeTestHelper.setField(service, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(service, "config", UltiTradeTestHelper.createDefaultConfig());
        UltiTradeTestHelper.setField(service, "settingsOperator", database.openAs(PlayerTradeSettings.class));
        return service;
    }

    /** Make {@code otherServer}'s {@code change} land between {@code serverA}'s next read and its write. */
    private void interleave(TradeLogService serverA, Runnable change) throws Exception {
        DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(serverA, "settingsOperator");
        UltiTradeTestHelper.setField(serverA, "settingsOperator", InterleavingOperator.beforeFirstUpdate(own, () -> {
            change.run();
            otherServerChanged = true;
        }));
    }

    private PlayerTradeSettings storedRow() {
        List<PlayerTradeSettings> rows = table.getAll(
                WhereCondition.builder().column("player_uuid").value(playerUuid.toString()).build());
        assertThat(rows).as("exactly one stored row for the player").hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("/trade toggle (off) keeps a block another server added; trading is off")
    void toggleOffKeepsABlockFromAnotherServer() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        assertThat(serverA.getSettings(playerUuid)).as("no row yet").isNull();
        serverB.toggleTrade(player);
        serverB.toggleTrade(player); // the row exists, trading on
        assertThat(serverA.isTradeEnabled(playerUuid)).as("server A has read the row: trading on").isTrue();
        UUID blocked = UUID.randomUUID();
        interleave(serverA, () -> serverB.blockPlayer(player, blocked));

        serverA.toggleTrade(player);

        assertThat(otherServerChanged).as("server B's block was made in the window").isTrue();
        PlayerTradeSettings row = storedRow();
        assertThat(row.isBlocked(blocked.toString())).as("server B's block is kept").isTrue();
        assertThat(row.isTradeEnabled()).as("server A's toggle is applied: off").isFalse();
    }

    @Test
    @DisplayName("/trade toggle (back on) keeps a block another server added; trading is on")
    void toggleOnKeepsABlockFromAnotherServer() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        serverB.toggleTrade(player); // the row exists, trading off
        assertThat(serverA.isTradeEnabled(playerUuid)).as("server A has read the row: trading off").isFalse();
        UUID blocked = UUID.randomUUID();
        interleave(serverA, () -> serverB.blockPlayer(player, blocked));

        serverA.toggleTrade(player);

        assertThat(otherServerChanged).isTrue();
        PlayerTradeSettings row = storedRow();
        assertThat(row.isBlocked(blocked.toString())).as("server B's block is kept").isTrue();
        assertThat(row.isTradeEnabled()).as("server A's toggle is applied: on").isTrue();
    }

    @Test
    @DisplayName("Two toggles on two servers both count: toggled off on B in the window, A's toggle turns it back on")
    void togglesOnTwoServersBothCount() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        serverB.toggleTrade(player);
        serverB.toggleTrade(player); // on
        assertThat(serverA.isTradeEnabled(playerUuid)).isTrue();
        interleave(serverA, () -> serverB.toggleTrade(player)); // B: off

        serverA.toggleTrade(player);

        assertThat(otherServerChanged).isTrue();
        assertThat(storedRow().isTradeEnabled())
                .as("A's toggle flips the stored state (off) rather than writing its own stale on-to-off").isTrue();
    }

    @Test
    @DisplayName("/trade block keeps trading turned off on another server; the block is added")
    void blockKeepsAToggleFromAnotherServer() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        serverB.toggleTrade(player);
        serverB.toggleTrade(player); // on
        assertThat(serverA.isTradeEnabled(playerUuid)).isTrue();
        interleave(serverA, () -> serverB.toggleTrade(player)); // B: off
        UUID blocked = UUID.randomUUID();

        assertThat(serverA.blockPlayer(player, blocked)).isTrue();

        assertThat(otherServerChanged).isTrue();
        PlayerTradeSettings row = storedRow();
        assertThat(row.isTradeEnabled()).as("server B's toggle is kept").isFalse();
        assertThat(row.isBlocked(blocked.toString())).as("server A's block is added").isTrue();
    }

    @Test
    @DisplayName("/trade unblock keeps a block another server added; only the named player is removed")
    void unblockKeepsABlockFromAnotherServer() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        serverB.blockPlayer(player, first);
        assertThat(serverA.isBlocked(playerUuid, first)).as("server A has read the row").isTrue();
        interleave(serverA, () -> serverB.blockPlayer(player, second));

        assertThat(serverA.unblockPlayer(player, first)).isTrue();

        assertThat(otherServerChanged).isTrue();
        PlayerTradeSettings row = storedRow();
        assertThat(row.isBlocked(second.toString())).as("server B's block is kept").isTrue();
        assertThat(row.isBlocked(first.toString())).as("server A's unblock is applied").isFalse();
    }

    @Test
    @DisplayName("/trade unblock keeps trading turned off on another server")
    void unblockKeepsAToggleFromAnotherServer() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        UUID first = UUID.randomUUID();
        serverB.blockPlayer(player, first);
        assertThat(serverA.isBlocked(playerUuid, first)).isTrue();
        interleave(serverA, () -> serverB.toggleTrade(player)); // B: off

        assertThat(serverA.unblockPlayer(player, first)).isTrue();

        assertThat(otherServerChanged).isTrue();
        PlayerTradeSettings row = storedRow();
        assertThat(row.isTradeEnabled()).as("server B's toggle is kept").isFalse();
        assertThat(row.isBlocked(first.toString())).isFalse();
    }

    @Test
    @DisplayName("A player who changed their name keeps another server's change; the new name and A's change are stored")
    void renamedPlayerKeepsAnotherServersChange() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        serverB.toggleTrade(player);
        serverB.toggleTrade(player); // on, stored under the old name
        assertThat(serverA.isTradeEnabled(playerUuid)).isTrue();
        Player renamed = UltiTradeTestHelper.createMockPlayer("Renamed", playerUuid);
        UUID blocked = UUID.randomUUID();
        interleave(serverA, () -> serverB.blockPlayer(player, blocked));

        serverA.toggleTrade(renamed);

        assertThat(otherServerChanged).isTrue();
        PlayerTradeSettings row = storedRow();
        assertThat(row.isBlocked(blocked.toString())).as("server B's block is kept").isTrue();
        assertThat(row.isTradeEnabled()).as("server A's toggle is applied").isFalse();
        assertThat(row.getPlayerName()).as("the player's current name is stored").isEqualTo("Renamed");
    }
}
