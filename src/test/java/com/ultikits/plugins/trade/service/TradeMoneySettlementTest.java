package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A trade's money is settled before anything else moves
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/58">UltiTrade#58</a>).
 *
 * <h2>The defect</h2>
 * {@code completeTrade} checked each payer's balance, then withdrew and deposited without reading the
 * withdrawal's {@code EconomyResponse}: a withdrawal refused after the check (the balance changed, for
 * example on another server sharing the economy's database) still paid the other player, and the items
 * still moved.
 *
 * <h2>The decision this implements (maintainer, 2026-10-04)</h2>
 * Withdraw both sides first. If either withdrawal is refused (or throws), refund what was already
 * withdrawn and cancel the whole trade: no items move, and both players are told the balance changed
 * and nothing was transferred. Only after both withdrawals succeed come the deposits and the item
 * exchange. A refused or throwing deposit refunds the payer; a refund that fails writes one SEVERE line
 * naming both players and the amount. No tax is taken on a cancelled trade.
 *
 * <h2>What makes a vacuous pass impossible here</h2>
 * The economy is a ledger kept by the test: every withdrawal and deposit the service makes changes the
 * balances it reports, and each call can be refused or made to throw. The assertions read those
 * balances, real inventories and the session's state.
 */
@DisplayName("A trade's money is settled before anything else moves (UltiTrade#58)")
class TradeMoneySettlementTest {

    private ServerMock server;
    private TradeService service;
    private PluginLogger logger;
    private Economy economy;
    private PlayerMock alice;
    private PlayerMock bob;

    /** The ledger: balance per player. */
    private final Map<UUID, Double> balances = new HashMap<>();
    /** Per player: refuse their next withdrawal / deposit, or throw from it. */
    private final Map<UUID, String> withdrawFault = new HashMap<>();
    private final Map<UUID, String> depositFault = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.tearDown();
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        balances.put(alice.getUniqueId(), 1000.0);
        balances.put(bob.getUniqueId(), 1000.0);

        UltiTrade plugin = mock(UltiTrade.class);
        logger = mock(PluginLogger.class);
        lenient().when(plugin.getLogger()).thenReturn(logger);
        lenient().when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        lenient().when(config.isEnableSounds()).thenReturn(false);
        lenient().when(config.isEnableParticles()).thenReturn(false);
        lenient().when(config.isEnableMoneyTrade()).thenReturn(true);
        lenient().when(config.getTradeTax()).thenReturn(0.1);
        lenient().when(config.getTradeCancelledMessage()).thenReturn("&cTrade cancelled!");
        lenient().when(config.getTradeCompleteMessage()).thenReturn("&aTrade completed!");

        economy = mock(Economy.class);
        lenient().when(economy.getBalance(any(OfflinePlayer.class)))
                .thenAnswer(inv -> balances.get(((OfflinePlayer) inv.getArgument(0)).getUniqueId()));
        lenient().when(economy.currencyNamePlural()).thenReturn("coins");
        lenient().when(economy.withdrawPlayer(any(OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
            UUID id = ((OfflinePlayer) inv.getArgument(0)).getUniqueId();
            double amount = inv.getArgument(1);
            String fault = withdrawFault.remove(id);
            if ("throw".equals(fault)) {
                throw new IllegalStateException("simulated economy storage error");
            }
            if ("refuse".equals(fault) || balances.get(id) < amount) {
                return new EconomyResponse(0, balances.get(id), EconomyResponse.ResponseType.FAILURE, "insufficient funds");
            }
            balances.put(id, balances.get(id) - amount);
            return new EconomyResponse(amount, balances.get(id), EconomyResponse.ResponseType.SUCCESS, "");
        });
        lenient().when(economy.depositPlayer(any(OfflinePlayer.class), anyDouble())).thenAnswer(inv -> {
            UUID id = ((OfflinePlayer) inv.getArgument(0)).getUniqueId();
            double amount = inv.getArgument(1);
            String fault = depositFault.get(id);
            if (fault != null && fault.endsWith("-always")) {
                fault = fault.substring(0, fault.length() - "-always".length());
            } else {
                depositFault.remove(id);
            }
            if ("throw".equals(fault)) {
                throw new IllegalStateException("simulated economy storage error");
            }
            if ("refuse".equals(fault)) {
                return new EconomyResponse(0, balances.get(id), EconomyResponse.ResponseType.FAILURE, "refused");
            }
            balances.put(id, balances.get(id) + amount);
            return new EconomyResponse(amount, balances.get(id), EconomyResponse.ResponseType.SUCCESS, "");
        });

        service = new TradeService();
        UltiTradeTestHelper.setField(service, "plugin", plugin);
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", mock(TradeLogService.class));
        UltiTradeTestHelper.setField(service, "economy", economy);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /** Alice stakes 4 emeralds and 100 coins, Bob 10 diamonds and 200 coins. */
    private TradeSession openTrade() throws Exception {
        TradeSession session = new TradeSession(alice, bob);
        session.setItem(alice.getUniqueId(), 0, new ItemStack(Material.EMERALD, 4));
        session.setItem(bob.getUniqueId(), 0, new ItemStack(Material.DIAMOND, 10));
        session.setMoney(alice.getUniqueId(), 100.0);
        session.setMoney(bob.getUniqueId(), 200.0);
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

    private double balance(PlayerMock player) {
        return balances.get(player.getUniqueId());
    }

    /** Neither player received the other's items; each got their own stake back. */
    private void assertNoItemsMoved() {
        assertThat(count(alice, Material.DIAMOND)).as("Alice did not receive Bob's diamonds").isZero();
        assertThat(count(bob, Material.EMERALD)).as("Bob did not receive Alice's emeralds").isZero();
        assertThat(count(alice, Material.EMERALD)).as("Alice's own stake is back").isEqualTo(4);
        assertThat(count(bob, Material.DIAMOND)).as("Bob's own stake is back").isEqualTo(10);
    }

    private static void assertToldNothingWasTransferred(PlayerMock player) {
        String message = null;
        String next;
        while ((next = player.nextMessage()) != null) {
            message = next;
        }
        assertThat(message).as("the last line " + player.getName() + " read")
                .contains("Trade cancelled").contains("balance changed").contains("nothing was transferred");
    }

    @Test
    @DisplayName("Control: both withdrawals and deposits succeed; money (less 10% tax) and items move")
    void controlEverythingMoves() throws Exception {
        TradeSession session = openTrade();

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
        assertThat(balance(alice)).isEqualTo(1000 - 100 + 180.0);
        assertThat(balance(bob)).isEqualTo(1000 - 200 + 90.0);
        assertThat(count(alice, Material.DIAMOND)).isEqualTo(10);
        assertThat(count(bob, Material.EMERALD)).isEqualTo(4);
    }

    @Test
    @DisplayName("The second side's withdrawal is refused: the first side is refunded, nothing is paid, no items move, both are told")
    void secondWithdrawalRefusedRefundsTheFirst() throws Exception {
        TradeSession session = openTrade();
        withdrawFault.put(bob.getUniqueId(), "refuse");

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(balance(alice)).as("Alice's 100 came back, no tax taken").isEqualTo(1000.0);
        assertThat(balance(bob)).as("Bob paid nothing and received nothing").isEqualTo(1000.0);
        assertNoItemsMoved();
        assertToldNothingWasTransferred(alice);
        assertToldNothingWasTransferred(bob);
    }

    @Test
    @DisplayName("The first side's withdrawal is refused: nothing is withdrawn from the second side, no items move")
    void firstWithdrawalRefusedTouchesNothingElse() throws Exception {
        TradeSession session = openTrade();
        withdrawFault.put(alice.getUniqueId(), "refuse");

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(balance(alice)).isEqualTo(1000.0);
        assertThat(balance(bob)).isEqualTo(1000.0);
        verify(economy, never()).withdrawPlayer(bob, 200.0);
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), org.mockito.ArgumentMatchers.eq(90.0));
        assertNoItemsMoved();
        assertToldNothingWasTransferred(alice);
        assertToldNothingWasTransferred(bob);
    }

    @Test
    @DisplayName("A withdrawal that throws counts as refused: the first side is refunded and the trade is cancelled")
    void throwingWithdrawalIsRefused() throws Exception {
        TradeSession session = openTrade();
        withdrawFault.put(bob.getUniqueId(), "throw");

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(balance(alice)).isEqualTo(1000.0);
        assertThat(balance(bob)).isEqualTo(1000.0);
        assertNoItemsMoved();
    }

    @Test
    @DisplayName("A deposit that throws refunds the payer; nothing else stays moved and no items move")
    void throwingDepositRefundsThePayer() throws Exception {
        TradeSession session = openTrade();
        depositFault.put(bob.getUniqueId(), "throw"); // Alice's 90 to Bob throws; Alice's refund is to Alice

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(balance(alice)).as("Alice refunded in full, no tax").isEqualTo(1000.0);
        assertThat(balance(bob)).as("Bob's own withdrawal refunded too").isEqualTo(1000.0);
        assertNoItemsMoved();
    }

    @Test
    @DisplayName("A refused deposit after the other deposit landed: that deposit is taken back, both payers refunded, no items move")
    void refusedSecondDepositUndoesTheFirst() throws Exception {
        TradeSession session = openTrade();
        depositFault.put(alice.getUniqueId(), "refuse"); // Bob's 180 to Alice is refused; Alice's 90 to Bob landed

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(balance(alice)).isEqualTo(1000.0);
        assertThat(balance(bob)).isEqualTo(1000.0);
        assertNoItemsMoved();
    }

    @Test
    @DisplayName("A refund that fails writes one SEVERE line naming both players, their UUIDs, the amount and the currency")
    void failedRefundIsLoggedSevere() throws Exception {
        TradeSession session = openTrade();
        withdrawFault.put(bob.getUniqueId(), "refuse");
        depositFault.put(alice.getUniqueId(), "throw-always"); // Alice's refund cannot be written

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertNoItemsMoved();
        verify(logger).error(argThat((String line) -> line.contains("Alice") && line.contains(alice.getUniqueId().toString())
                && line.contains("Bob") && line.contains(bob.getUniqueId().toString())
                && line.contains("100") && line.contains("coins")));
    }

    @Test
    @DisplayName("The SEVERE line keeps the amount's full precision, so the operator can restore it exactly (Codex run 1 on #59)")
    void failedRefundLineKeepsFullPrecision() throws Exception {
        TradeSession session = openTrade();
        session.setMoney(alice.getUniqueId(), 0.005);
        withdrawFault.put(bob.getUniqueId(), "refuse");
        depositFault.put(alice.getUniqueId(), "throw-always");

        service.completeTrade(session);

        verify(logger).error(argThat((String line) -> line.contains("Alice") && line.contains("0.005")));
    }

    @Test
    @DisplayName("Experience is checked before any money moves: too little experience cancels with every balance untouched")
    void experienceShortfallCancelsBeforeMoneyMoves() throws Exception {
        TradeSession session = openTrade();
        session.setExp(bob.getUniqueId(), 5000); // Bob has no experience

        service.completeTrade(session);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(balance(alice)).isEqualTo(1000.0);
        assertThat(balance(bob)).isEqualTo(1000.0);
        assertNoItemsMoved();
    }
}
