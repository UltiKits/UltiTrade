package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.entity.TradeLogData;
import com.ultikits.plugins.trade.entity.TradeSession;
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
import static org.assertj.core.api.Assertions.within;

/**
 * A player's post-trade statistics on servers that share one database
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/54">UltiTrade#54</a>, plan 17-83 Task 2).
 *
 * <h2>The defect</h2>
 * Each completed trade added to the player's {@code total_trades}, {@code total_money_traded} and
 * {@code total_exp_traded} in the server's cached copy and wrote that copy back, so a trade the player
 * completed on another server in the meantime was lost from the totals.
 *
 * <h2>The decision this implements (maintainer, 2026-10-06 00:04)</h2>
 * The statistics are incremented as a compare-and-set loop: re-read, add, write only if the row still
 * holds what was read, retry on a miss. Two servers' increments both count.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * Each server is a real {@link TradeLogService} with its own operators on one SQLite file, completing
 * trades through {@link TradeLogService#logCompletedTrade}; the assertions read the table.
 */
@DisplayName("Post-trade statistics count every server's trades (UltiTrade#54)")
class TradeStatsSharedDatabaseTest {

    @TempDir
    Path dir;

    private SharedSqliteDatabase database;
    private DataOperator<PlayerTradeSettings> table;
    private UUID playerUuid;
    private Player player;
    private Player partnerOnA;
    private Player partnerOnB;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        database = SharedSqliteDatabase.in(dir);
        table = database.openAs(PlayerTradeSettings.class);
        playerUuid = UUID.randomUUID();
        player = UltiTradeTestHelper.createMockPlayer("Counter", playerUuid);
        partnerOnA = UltiTradeTestHelper.createMockPlayer("PartnerA", UUID.randomUUID());
        partnerOnB = UltiTradeTestHelper.createMockPlayer("PartnerB", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** One server: its own service and its own operators on the shared file. Writes run inline. */
    private TradeLogService server() throws Exception {
        TradeLogService service = new TradeLogService();
        UltiTradeTestHelper.setField(service, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(service, "config", UltiTradeTestHelper.createDefaultConfig());
        UltiTradeTestHelper.setField(service, "settingsOperator", database.openAs(PlayerTradeSettings.class));
        UltiTradeTestHelper.setField(service, "logOperator", database.openAs(TradeLogData.class));
        return service;
    }

    /** {@code server} completes one trade in which the player gives {@code money} and {@code exp}. */
    private void completeTrade(TradeLogService server, Player partner, double money, int exp) {
        TradeSession session = new TradeSession(player, partner);
        session.setMoney(playerUuid, money);
        session.setExp(playerUuid, exp);
        server.logCompletedTrade(session, player, partner, 0.0, 0);
    }

    private PlayerTradeSettings storedRow() {
        List<PlayerTradeSettings> rows = table.getAll(
                WhereCondition.builder().column("player_uuid").value(playerUuid.toString()).build());
        assertThat(rows).as("exactly one stored row for the player").hasSize(1);
        return rows.get(0);
    }

    @Test
    @DisplayName("A trade on server B between two trades on server A: all three count")
    void tradesOnTwoServersAllCount() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();

        completeTrade(serverA, partnerOnA, 10.0, 5);
        completeTrade(serverB, partnerOnB, 20.0, 7);
        completeTrade(serverA, partnerOnA, 0.1, 11);

        PlayerTradeSettings row = storedRow();
        assertThat(row.getTotalTrades()).as("three trades").isEqualTo(3);
        assertThat(row.getTotalMoneyTraded()).as("money given in all three").isCloseTo(30.1, within(1e-9));
        assertThat(row.getTotalExpTraded()).as("experience given in all three").isEqualTo(23);
    }

    @Test
    @DisplayName("Server B's trade lands between server A's read and its write: both increments count")
    void interleavedIncrementsBothCount() throws Exception {
        TradeLogService serverA = server();
        TradeLogService serverB = server();
        completeTrade(serverA, partnerOnA, 1.0, 1);
        DataOperator<PlayerTradeSettings> own = UltiTradeTestHelper.getField(serverA, "settingsOperator");
        boolean[] ran = {false};
        UltiTradeTestHelper.setField(serverA, "settingsOperator", InterleavingOperator.beforeFirstUpdate(own, () -> {
            completeTrade(serverB, partnerOnB, 2.0, 3);
            ran[0] = true;
        }));

        completeTrade(serverA, partnerOnA, 4.0, 5);

        assertThat(ran[0]).as("server B's trade completed in the window").isTrue();
        PlayerTradeSettings row = storedRow();
        assertThat(row.getTotalTrades()).isEqualTo(3);
        assertThat(row.getTotalMoneyTraded()).isCloseTo(7.0, within(1e-9));
        assertThat(row.getTotalExpTraded()).isEqualTo(9);
    }

    @Test
    @DisplayName("A total raised directly in the database while the server runs is kept: the next trade adds one to it")
    void totalRaisedInTheDatabaseIsKept() throws Exception {
        TradeLogService serverA = server();
        completeTrade(serverA, partnerOnA, 1.0, 1);
        PlayerTradeSettings raised = storedRow();
        raised.setTotalTrades(raised.getTotalTrades() + 5);
        assertThat(table.updateCounted(raised)).as("precondition: the raise reached the row").isEqualTo(1);

        completeTrade(serverA, partnerOnA, 1.0, 1);

        assertThat(storedRow().getTotalTrades()).as("the raised value plus one").isEqualTo(7);
    }
}
