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

/**
 * Trade settings on servers that share one database
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/54">UltiTrade#54</a>) -- the tracer of the
 * shared-database stale-cache class (Phase 17 plan 17-83).
 *
 * <h2>The defect</h2>
 * A server kept a player's settings row in memory for its whole lifetime and wrote that whole copy back on
 * every change and again at shutdown. A change another server made in between was reverted.
 *
 * <h2>The decision this implements (maintainer, 2026-10-06 00:04)</h2>
 * The cache is read-only and scoped to the player's time on the server; nothing is written back at
 * shutdown; every change re-reads the row and writes it with {@code DataOperator#updateIf} on the values it
 * read, re-reading and re-applying on a miss -- UltiEconomy's pattern.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * Each "server" is a real {@link TradeLogService} with its own operator on one SQLite file, so the
 * assertions read the table itself. The first assertion of each test checks the other server's change
 * really reached the row before this server acts.
 */
@DisplayName("Trade settings changed on another server sharing the database are kept (UltiTrade#54)")
class TradeSettingsSharedDatabaseTest {

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
        player = UltiTradeTestHelper.createMockPlayer("Sharer", playerUuid);
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

    private PlayerTradeSettings storedRow() {
        List<PlayerTradeSettings> rows = table.getAll(
                WhereCondition.builder().column("player_uuid").value(playerUuid.toString()).build());
        assertThat(rows).as("exactly one stored row for the player").hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("Trading turned off on server B survives server A's later blocklist change and A's stop; A's block is kept too")
    void blocklistChangeAndStopKeepAnotherServersToggle() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        assertThat(serverA.getOrCreateSettings(playerUuid, "Sharer").isTradeEnabled())
                .as("server A has read the player's settings: trading on").isTrue();

        serverB.toggleTrade(player);
        assertThat(storedRow().isTradeEnabled()).as("precondition: server B's toggle reached the row").isFalse();

        UUID blocked = UUID.randomUUID();
        assertThat(serverA.blockPlayer(player, blocked)).as("server A's block is applied").isTrue();
        serverA.shutdown();

        PlayerTradeSettings row = storedRow();
        assertThat(row.isTradeEnabled()).as("server B's change is not reverted").isFalse();
        assertThat(row.isBlocked(blocked.toString())).as("server A's own change is kept").isTrue();
    }
}
