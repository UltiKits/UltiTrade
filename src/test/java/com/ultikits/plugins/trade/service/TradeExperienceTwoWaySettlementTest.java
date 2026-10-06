package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * A trade in which both players offer experience and one of them ends above the int range
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/65">UltiTrade#65</a>; Codex run 1 on PR #66, P1).
 *
 * <h2>The defect</h2>
 * The settlement rebuilt player 1, paid player 2, and only then read player 2's total to rebuild player 2. When player
 * 2's total was readable before the trade but exceeded {@code Integer.MAX_VALUE} after receiving player 1's offer, that
 * read was the capped value, and player 2 was rebuilt from the cap less their offer: at 21,000 levels, receiving
 * 200,000,000 and offering 100, player 2 ended at 21,863 levels instead of 22,033.
 *
 * <h2>The instrument</h2>
 * MockBukkit keeps a player's total in an {@code int} and refuses to go past it, so this test uses a player whose
 * experience follows vanilla's arithmetic (level, progress inside the level, points to the next level) in {@code double},
 * the way a Paper player keeps its level and progress past the {@code int} range of its own total counter.
 */
@DisplayName("Both senders are rebuilt from their totals before any experience moves (UltiTrade#65, Codex run 1 P1)")
class TradeExperienceTwoWaySettlementTest {

    private TradeService service;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        UltiTrade plugin = mock(UltiTrade.class);
        lenient().when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        lenient().when(config.isEnableSounds()).thenReturn(false);
        lenient().when(config.isEnableParticles()).thenReturn(false);
        lenient().when(config.isEnableExpTrade()).thenReturn(true);
        lenient().when(config.getExpTaxRate()).thenReturn(0.0);
        service = new TradeService();
        UltiTradeTestHelper.setField(service, "plugin", plugin);
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", mock(TradeLogService.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** Vanilla's points needed to go from {@code level} to the next. */
    private static int expToLevel(int level) {
        return level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    /** A player at {@code level} with no progress, whose experience follows vanilla's level arithmetic. */
    private static Player experiencePlayer(String name, int level) {
        Player player = UltiTradeTestHelper.createMockPlayer(name, UUID.randomUUID());
        int[] lvl = {level};
        double[] progress = {0.0};
        lenient().when(player.getLevel()).thenAnswer(inv -> lvl[0]);
        lenient().when(player.getExp()).thenAnswer(inv -> (float) progress[0]);
        lenient().when(player.getExpToLevel()).thenAnswer(inv -> expToLevel(lvl[0]));
        doAnswer(inv -> { lvl[0] = inv.getArgument(0); return null; }).when(player).setLevel(anyInt());
        doAnswer(inv -> { progress[0] = (float) inv.getArgument(0); return null; }).when(player).setExp(anyFloat());
        doAnswer(inv -> null).when(player).setTotalExperience(anyInt());
        doAnswer(inv -> {
            long points = (int) inv.getArgument(0);
            // Whole levels first (exact), then the rest as progress -- vanilla's loop, without float drift.
            double carried = progress[0] * expToLevel(lvl[0]) + points;
            while (carried >= expToLevel(lvl[0])) {
                carried -= expToLevel(lvl[0]);
                lvl[0]++;
            }
            progress[0] = carried / expToLevel(lvl[0]);
            return null;
        }).when(player).giveExp(anyInt());
        return player;
    }

    @Test
    @DisplayName("Both at 21,000 levels; A offers 200,000,000 and B 100: B ends at 22,033 levels, A where their own total less 200,000,000 plus 100 puts them")
    void eachSenderIsRebuiltFromThePreTradeTotal() throws Exception {
        Player a = experiencePlayer("A", 21_000);
        Player b = experiencePlayer("B", 21_000);
        UUID aId = a.getUniqueId();
        UUID bId = b.getUniqueId();
        doReturn(a).when(Bukkit.getServer()).getPlayer(aId);
        doReturn(b).when(Bukkit.getServer()).getPlayer(bId);
        TradeSession session = new TradeSession(a, b);
        session.setExp(a.getUniqueId(), 200_000_000);
        session.setExp(b.getUniqueId(), 100);
        Map<UUID, TradeSession> active = UltiTradeTestHelper.getField(service, "activeSessions");
        Map<UUID, UUID> bySession = UltiTradeTestHelper.getField(service, "playerSessionMap");
        active.put(session.getSessionId(), session);
        bySession.put(a.getUniqueId(), session.getSessionId());
        bySession.put(b.getUniqueId(), session.getSessionId());

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
        // B: 1,981,089,720 - 100 + 200,000,000 = 2,181,089,620 points = level 22,033 by Minecraft's table. Rebuilt from
        // the capped total read after receiving, B ends at 21,863 levels.
        assertThat(b.getLevel()).as("B keeps their own total less 100, plus A's 200,000,000").isEqualTo(22_033);
        // A: 1,981,089,720 - 200,000,000 + 100 = 1,781,089,820 points = level 19,912 by the table.
        assertThat(a.getLevel()).isEqualTo(19_912);
    }

    /** Register a trade between {@code a} and {@code b}, offering {@code expA} and {@code expB}. */
    private TradeSession openTrade(Player a, Player b, int expA, int expB) throws Exception {
        UUID aId = a.getUniqueId();
        UUID bId = b.getUniqueId();
        doReturn(a).when(Bukkit.getServer()).getPlayer(aId);
        doReturn(b).when(Bukkit.getServer()).getPlayer(bId);
        TradeSession session = new TradeSession(a, b);
        session.setExp(aId, expA);
        session.setExp(bId, expB);
        Map<UUID, TradeSession> active = UltiTradeTestHelper.getField(service, "activeSessions");
        Map<UUID, UUID> bySession = UltiTradeTestHelper.getField(service, "playerSessionMap");
        active.put(session.getSessionId(), session);
        bySession.put(aId, session.getSessionId());
        bySession.put(bId, session.getSessionId());
        return session;
    }

    @Test
    @DisplayName("Two offers of 1,500,000,000 each are a large trade: the confirmation page opens, the int sum does not wrap below the threshold (Codex run 2, P2)")
    void twoHugeOffersNeedTheLargeTradeConfirmation() throws Exception {
        Player a = experiencePlayer("A", 21_000);
        Player b = experiencePlayer("B", 21_000);
        openTrade(a, b, 1_500_000_000, 1_500_000_000);

        service.confirmTrade(a);

        java.util.Set<UUID> transitions = UltiTradeTestHelper.getField(service, "confirmPageTransitions");
        assertThat(transitions).as("the confirmation page is being opened for A").contains(a.getUniqueId());
    }

    @Test
    @DisplayName("At a 100% experience tax two offers of 1,500,000,000 log a tax of 3,000,000,000, not a wrapped negative number (Codex run 2, P2)")
    void taxOfTwoHugeOffersIsLoggedExactly(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        TradeConfig config = UltiTradeTestHelper.getField(service, "config");
        lenient().when(config.getExpTaxRate()).thenReturn(1.0);
        com.ultikits.plugins.trade.testsupport.SharedSqliteDatabase database =
                com.ultikits.plugins.trade.testsupport.SharedSqliteDatabase.in(dir);
        TradeLogService log = new TradeLogService();
        UltiTradeTestHelper.setField(log, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(log, "config", config);
        UltiTradeTestHelper.setField(log, "settingsOperator", database.openAs(com.ultikits.plugins.trade.entity.PlayerTradeSettings.class));
        com.ultikits.ultitools.interfaces.DataOperator<com.ultikits.plugins.trade.entity.TradeLogData> logs =
                database.openAs(com.ultikits.plugins.trade.entity.TradeLogData.class);
        UltiTradeTestHelper.setField(log, "logOperator", logs);
        UltiTradeTestHelper.setField(service, "logService", log);
        Player a = experiencePlayer("A", 21_000);
        Player b = experiencePlayer("B", 21_000);
        TradeSession session = openTrade(a, b, 1_500_000_000, 1_500_000_000);

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
        java.util.List<com.ultikits.plugins.trade.entity.TradeLogData> rows = logs.getAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getExpTaxCollected()).isEqualTo(3_000_000_000L);
    }
}
