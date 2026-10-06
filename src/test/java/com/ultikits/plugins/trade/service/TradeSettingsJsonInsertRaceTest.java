package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.entity.TradeLogData;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.testsupport.InterleavingOperator;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;

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
import static org.mockito.Mockito.mock;

/**
 * Creating a player's first settings row on the JSON backend while another writer on the same server creates it too
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/54">UltiTrade#54</a>; Codex run 1 on PR #66, P2).
 *
 * <h2>The defect</h2>
 * The JSON store ignores an insert whose id it already holds ({@code putIfAbsent}) and returns normally. A change that
 * found no row inserted the row with the change already applied; when the trade-statistics task had inserted the
 * player's row in between, that insert was ignored, yet the row now existed, so the change was reported as written
 * while it was not stored.
 *
 * <h2>The rule</h2>
 * A missing row is created first without the change, and the change is then applied to whatever row is stored, through
 * the same conditional write as every other change. Both writers' changes end up stored.
 */
@DisplayName("A first row created at once by two writers on the JSON backend keeps both writers' changes (UltiTrade#54)")
class TradeSettingsJsonInsertRaceTest {

    @TempDir
    Path dir;

    private UUID playerUuid;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        playerUuid = UUID.randomUUID();
        player = UltiTradeTestHelper.createMockPlayer("Jsoner", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @SuppressWarnings("unchecked")
    private TradeLogService writer(DataOperator<PlayerTradeSettings> settings) throws Exception {
        TradeLogService service = new TradeLogService();
        UltiTradeTestHelper.setField(service, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(service, "config", UltiTradeTestHelper.createDefaultConfig());
        UltiTradeTestHelper.setField(service, "settingsOperator", settings);
        UltiTradeTestHelper.setField(service, "logOperator", mock(DataOperator.class));
        return service;
    }

    @Test
    @DisplayName("The statistics task inserts the row between the toggle's read and its insert: trading is off AND the trade is counted")
    void bothFirstRowWritersAreStored() throws Exception {
        SimpleJsonDataOperator<PlayerTradeSettings> store =
                new SimpleJsonDataOperator<>(dir.resolve("trade_player_settings").toString(), PlayerTradeSettings.class);
        TradeLogService statistics = writer(store);
        Player partner = UltiTradeTestHelper.createMockPlayer("Partner", UUID.randomUUID());
        TradeLogService commands = writer(InterleavingOperator.beforeFirstInsert(store,
                () -> statistics.logCompletedTrade(new TradeSession(player, partner), player, partner, 0.0, 0)));

        boolean reply = commands.toggleTrade(player);

        List<PlayerTradeSettings> rows = store.getAll(
                WhereCondition.builder().column("player_uuid").value(playerUuid.toString()).build());
        assertThat(rows).as("one row for the player").hasSize(1);
        assertThat(rows.get(0).getTotalTrades()).as("the statistics task's trade is counted").isEqualTo(1);
        assertThat(rows.get(0).isTradeEnabled()).as("the toggle is stored, not only reported").isFalse();
        assertThat(reply).as("the reply matches the stored state").isFalse();
    }
}
