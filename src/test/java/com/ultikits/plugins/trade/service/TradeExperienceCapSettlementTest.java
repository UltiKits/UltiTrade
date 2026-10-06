package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * An experience offer from a player whose experience total cannot be read exactly
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/65">UltiTrade#65</a>, gate-1 finding F1 of PR #66; maintainer decision
 * 2026-10-06: refuse experience offers while the total is at the cap).
 *
 * <h2>The defect</h2>
 * From 21,864 levels a player's true total exceeds {@code Integer.MAX_VALUE}, so the module reads the capped value. The
 * settlement rebuilt the sender's experience as {@code capped total - offer}: a player at 25,000 levels who offered 100
 * points lost about 660 million points more than they offered.
 *
 * <h2>The rule</h2>
 * Such an offer is refused before anything moves: the trade is cancelled with a reason saying the player's experience is too
 * high to be counted exactly, and nobody's experience, money or items move. A player whose total is exact trades as before
 * (control).
 */
@DisplayName("An experience offer from a player whose total is at the cap is refused before anything moves (UltiTrade#65 F1)")
class TradeExperienceCapSettlementTest {

    private ServerMock server;
    private TradeService service;
    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.tearDown();
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");

        UltiTrade plugin = mock(UltiTrade.class);
        lenient().when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        lenient().when(config.isEnableSounds()).thenReturn(false);
        lenient().when(config.isEnableParticles()).thenReturn(false);
        lenient().when(config.isEnableExpTrade()).thenReturn(true);
        lenient().when(config.getExpTaxRate()).thenReturn(0.0);
        lenient().when(config.getTradeCancelledMessage()).thenReturn("&cTrade cancelled!");
        lenient().when(config.getTradeCompleteMessage()).thenReturn("&aTrade completed!");

        service = new TradeService();
        UltiTradeTestHelper.setField(service, "plugin", plugin);
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", mock(TradeLogService.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** Alice stakes 4 emeralds and offers {@code exp} experience; Bob stakes 10 diamonds. */
    private TradeSession openTrade(int exp) throws Exception {
        TradeSession session = new TradeSession(alice, bob);
        session.setItem(alice.getUniqueId(), 0, new ItemStack(Material.EMERALD, 4));
        session.setItem(bob.getUniqueId(), 0, new ItemStack(Material.DIAMOND, 10));
        session.setExp(alice.getUniqueId(), exp);
        Map<UUID, TradeSession> active = UltiTradeTestHelper.getField(service, "activeSessions");
        Map<UUID, UUID> bySession = UltiTradeTestHelper.getField(service, "playerSessionMap");
        active.put(session.getSessionId(), session);
        bySession.put(alice.getUniqueId(), session.getSessionId());
        bySession.put(bob.getUniqueId(), session.getSessionId());
        return session;
    }

    private static int count(PlayerMock player, Material material) {
        int n = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                n += item.getAmount();
            }
        }
        return n;
    }

    private static String lastMessage(PlayerMock player) {
        String last = null;
        String next;
        while ((next = player.nextMessage()) != null) {
            last = next;
        }
        return last;
    }

    @ParameterizedTest(name = "Alice at {0} levels")
    @ValueSource(ints = {21_864, 21_865, 25_000})
    @DisplayName("Alice's total is at the cap: the trade is cancelled with the reason, and no experience, item or level moves")
    void offerAtTheCapIsRefused(int level) throws Exception {
        alice.setLevel(level);
        alice.setExp(0.5f);
        bob.setLevel(10);
        TradeSession session = openTrade(100);

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(alice.getLevel()).as("Alice's level is untouched").isEqualTo(level);
        assertThat(alice.getExp()).as("Alice's progress is untouched").isEqualTo(0.5f);
        assertThat(bob.getLevel()).as("Bob received no experience").isEqualTo(10);
        assertThat(count(bob, Material.EMERALD)).as("no items moved").isZero();
        assertThat(count(alice, Material.DIAMOND)).isZero();
        String reason = CatalogueText.text("en", "cancel_reason_exp_unreadable").replace("{PLAYER}", "Alice");
        assertThat(lastMessage(alice)).contains(reason);
    }

    @Test
    @DisplayName("Control: at 30 levels Alice's offer of 100 moves; the trade completes")
    void exactTotalStillTrades() throws Exception {
        alice.setLevel(30);
        alice.setExp(0.0f);
        bob.setLevel(0);
        TradeSession session = openTrade(100);

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
        assertThat(service.getTotalExperience(alice)).as("1395 - 100").isEqualTo(1295);
        assertThat(count(bob, Material.EMERALD)).isEqualTo(4);
    }

    @Test
    @DisplayName("Both offer: each sender is rebuilt from their total before anything moves, never from a total capped after receiving (Codex run 1, P1)")
    void eachSenderIsRebuiltFromThePreTradeTotal() throws Exception {
        // Both at 21,000 levels (1,981,089,720 points, readable). Alice offers 200,000,000, Bob 100. Bob's total after
        // receiving Alice's offer exceeds the int range: rebuilding Bob from it would take his remainder off the cap.
        alice.setLevel(21_000);
        alice.setExp(0.0f);
        bob.setLevel(21_000);
        bob.setExp(0.0f);
        TradeSession session = openTrade(200_000_000);
        session.setExp(bob.getUniqueId(), 100);

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
        // 1,981,089,720 - 100 + 200,000,000 = 2,181,089,620 points: level 22,033 by Minecraft's table. Rebuilt from the
        // cap instead, Bob ends below 21,864 levels.
        assertThat(bob.getLevel()).as("Bob keeps his own total less 100, plus Alice's 200,000,000").isBetween(22_030, 22_036);
    }
}
