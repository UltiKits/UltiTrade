package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.ultitools.annotations.Scheduled;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Deterministic deadline checks with real inventories, without sleeping or a live server. */
@DisplayName("Optional open-trade lifetime (UltiTrade#41)")
class TradeWindowTimeoutTest {
    private ServerMock server;
    private TradeService service;
    private TradeConfig config;
    private TradeLogService log;
    private Economy economy;
    private PlayerMock alice;
    private PlayerMock bob;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.tearDown();
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        config = new TradeConfig();
        config.setEnableSounds(false);
        config.setEnableParticles(false);
        config.setTradeCancelledMessage("&cTrade cancelled!");
        config.setTradeCompleteMessage("&aTrade completed!");
        UltiTrade plugin = mock(UltiTrade.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        log = mock(TradeLogService.class);
        economy = UltiTradeTestHelper.createMockEconomy();
        service = new TradeService();
        UltiTradeTestHelper.setField(service, "plugin", plugin);
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", log);
        UltiTradeTestHelper.setField(service, "economy", economy);
        UltiTradeTestHelper.setField(service, "bukkitPlugin", MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    // Reflection keeps the RED tests compilable against the baseline which has no timeout API.
    private void limit(int seconds) throws Exception {
        TradeConfig.class.getMethod("setWindowTimeout", int.class).invoke(config, seconds);
    }

    private TradeSession open(int seconds) throws Exception {
        limit(seconds);
        service.startTrade(alice, bob);
        TradeSession session = service.getSession(alice.getUniqueId());
        assertThat(session).as("both initial windows must really open").isNotNull();
        return session;
    }

    private static long started(TradeSession session) throws Exception {
        return UltiTradeTestHelper.<Long>getField(session, "windowStartNanos");
    }

    private void at(TradeSession session, long elapsedNanos) throws Exception {
        Method sweep = TradeService.class.getDeclaredMethod("cleanupExpiredTradeWindows", long.class);
        sweep.setAccessible(true);
        sweep.invoke(service, started(session) + elapsedNanos);
    }

    private static long seconds(int seconds) {
        return TimeUnit.SECONDS.toNanos(seconds);
    }

    private static List<String> messages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        String message;
        while ((message = player.nextMessage()) != null) {
            messages.add(ChatColor.stripColor(message));
        }
        return messages;
    }

    private static void expireNow(TradeSession session) throws Exception {
        UltiTradeTestHelper.setField(session, "windowStartNanos", System.nanoTime() - seconds(601));
    }

    private static ItemStack namedStake() {
        ItemStack stack = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("Alice's stake");
        meta.setLore(Collections.singletonList("return unchanged"));
        stack.setItemMeta(meta);
        return stack;
    }

    @Test
    @DisplayName("New key and old constructor preserve unlimited trades by default")
    void defaultIsUnlimited() throws Exception {
        assertThat(TradeConfig.class.getMethod("getWindowTimeout").invoke(config)).isEqualTo(0);
        TradeSession session = open(0);
        at(session, seconds(10000));
        assertThat(service.getSession(alice.getUniqueId())).isSameAs(session);
        assertThat(messages(alice)).isEmpty();
        assertThat(messages(bob)).isEmpty();
        assertThat(TradeSession.class.getMethod("getWindowSecondsRemaining", long.class)
                .invoke(new TradeSession(alice, bob), Long.MAX_VALUE)).isEqualTo(-1);
    }

    @Test
    @DisplayName("Expiry at the exact boundary returns each original stake and moves no money or experience")
    void expiryReturnsStakesExactlyOnce() throws Exception {
        TradeSession session = open(30);
        ItemStack sword = namedStake();
        session.setItem(alice.getUniqueId(), 0, sword);
        session.setItem(bob.getUniqueId(), 0, new ItemStack(Material.EMERALD, 3));
        alice.giveExp(100);
        bob.giveExp(200);
        int aliceExp = service.getTotalExperience(alice);
        int bobExp = service.getTotalExperience(bob);
        session.setMoney(alice.getUniqueId(), 50);
        session.setExp(bob.getUniqueId(), 30);
        messages(alice);
        messages(bob);

        at(session, seconds(30) - 1);
        assertThat(service.isTrading(alice.getUniqueId())).isTrue();
        at(session, seconds(30));
        at(session, seconds(40));

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(service.getSession(alice.getUniqueId())).isNull();
        assertThat(service.getSession(bob.getUniqueId())).isNull();
        assertThat(alice.getInventory().containsAtLeast(sword, 1)).isTrue();
        assertThat(alice.getInventory().contains(Material.EMERALD)).isFalse();
        assertThat(bob.getInventory().containsAtLeast(new ItemStack(Material.EMERALD), 3)).isTrue();
        assertThat(bob.getInventory().contains(Material.DIAMOND_SWORD)).isFalse();
        assertThat(service.getTotalExperience(alice)).isEqualTo(aliceExp);
        assertThat(service.getTotalExperience(bob)).isEqualTo(bobExp);
        verifyNoInteractions(economy);
        verify(log).logCancelledTrade(session, "trade window time limit reached; nothing was transferred");
        assertThat(messages(alice)).filteredOn(s -> s.startsWith("Trade cancelled!"))
                .containsExactly("Trade cancelled! (trade window time limit reached; nothing was transferred)");
    }

    @Test
    @DisplayName("Overflow uses the existing owner-foot drop path, never the partner inventory")
    void expiryDropsOverflowAtOwner() throws Exception {
        TradeSession session = open(30);
        session.setItem(alice.getUniqueId(), 0, new ItemStack(Material.DIAMOND, 7));
        // MockBukkit's generic addItem scans its whole inventory, including equipment slots.
        for (int slot = 0; slot < alice.getInventory().getSize(); slot++) {
            alice.getInventory().setItem(slot, new ItemStack(Material.SHIELD));
        }
        at(session, seconds(30));
        List<Item> drops = alice.getWorld().getEntitiesByClass(Item.class).stream()
                .filter(item -> item.getItemStack().getType() == Material.DIAMOND)
                .collect(java.util.stream.Collectors.toList());
        assertThat(drops).hasSize(1);
        assertThat(drops.get(0).getItemStack().getAmount()).isEqualTo(7);
        assertThat(drops.get(0).getLocation().distance(alice.getLocation())).isLessThan(2);
        assertThat(bob.getInventory().contains(Material.DIAMOND)).isFalse();
    }

    @Test
    @DisplayName("Both players get the lifetime notice, then one rounded-up warning in the last ten seconds")
    void noticesAndWarningAreOncePerSession() throws Exception {
        TradeSession session = open(30);
        String opening = "This trade will close after 30 seconds. Changing offers does not restart the timer.";
        assertThat(messages(alice)).containsExactly(opening);
        assertThat(messages(bob)).containsExactly(opening);
        at(session, seconds(20) - 1);
        assertThat(messages(alice)).isEmpty();
        at(session, seconds(20));
        at(session, seconds(25));
        String warning = "This trade closes in 10 seconds. Confirm or cancel before time runs out.";
        assertThat(messages(alice)).containsExactly(warning);
        assertThat(messages(bob)).containsExactly(warning);
    }

    @Test
    @DisplayName("A short lifetime warns on the first scan, rounding a partial second up")
    void shortLifetimeWarning() throws Exception {
        TradeSession session = open(5);
        messages(alice);
        messages(bob);
        at(session, seconds(1) + 1);
        assertThat(messages(alice)).containsExactly(
                "This trade closes in 4 seconds. Confirm or cancel before time runs out.");
    }

    @Test
    @DisplayName("A lag jump directly past expiry cancels rather than sending a stale warning")
    void lagJumpExpiresWithoutWarning() throws Exception {
        TradeSession session = open(30);
        messages(alice);
        at(session, seconds(80));
        assertThat(messages(alice)).hasSize(1).allMatch(s -> s.startsWith("Trade cancelled!"));
        assertThat(service.isTrading(alice.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("Offer edits and confirming or unconfirming never move the original deadline")
    void activityDoesNotResetDeadline() throws Exception {
        TradeSession session = open(30);
        long original = started(session);
        session.setItem(alice.getUniqueId(), 0, new ItemStack(Material.DIAMOND));
        session.setMoney(alice.getUniqueId(), 10);
        session.setExp(bob.getUniqueId(), 10);
        service.confirmTrade(alice);
        service.cancelConfirmation(alice);
        assertThat(started(session)).isEqualTo(original);
        at(session, seconds(30));
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
    }

    @Test
    @DisplayName("Reload cannot extend or disable an existing window; new sessions capture the edited setting")
    void reloadOnlyAffectsNewSessions() throws Exception {
        TradeSession first = open(30);
        limit(60);
        service.resetConfirmationsAfterReload();
        service.refreshOpenTradeWindowsAfterReload();
        at(first, seconds(30));
        assertThat(first.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        service.startTrade(alice, bob);
        TradeSession second = service.getSession(alice.getUniqueId());
        assertThat(second).isNotNull().isNotSameAs(first);
        limit(0);
        at(second, seconds(30));
        assertThat(service.getSession(alice.getUniqueId())).isSameAs(second);
        at(second, seconds(60));
        assertThat(second.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
    }

    @Test
    @DisplayName("Enabling the setting leaves an already-unlimited window unlimited")
    void reloadDoesNotEnableOldWindows() throws Exception {
        TradeSession session = open(0);
        limit(30);
        service.resetConfirmationsAfterReload();
        at(session, seconds(1000));
        assertThat(service.getSession(alice.getUniqueId())).isSameAs(session);
    }

    @Test
    @DisplayName("Normal confirmation after the deadline cannot race the scheduled scan into settlement")
    void confirmationAfterDeadlineCancels() throws Exception {
        TradeSession session = open(30);
        session.setConfirmed(bob.getUniqueId(), true);
        expireNow(session);
        service.confirmTrade(alice);
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(session.isConfirmed(alice.getUniqueId())).isFalse();
        verifyNoInteractions(economy);
        verify(log, never()).logCompletedTrade(any(), any(), any(), anyDouble(), anyLong());
    }

    @Test
    @DisplayName("A large-trade confirmation callback checks expiry before confirming or reopening")
    void confirmationPageAfterDeadlineCancels() throws Exception {
        TradeSession session = open(30);
        config.setConfirmThreshold(1);
        session.setMoney(alice.getUniqueId(), 10);
        service.confirmTrade(alice);
        server.getScheduler().performOneTick();
        TradeConfirmPage page = (TradeConfirmPage) alice.getOpenInventory().getTopInventory().getHolder();
        assertThat(page).isNotNull();
        expireNow(session);
        UltiTradeTestHelper.<Runnable>getField(page, "onConfirm").run();
        server.getScheduler().performOneTick();
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(service.getSession(alice.getUniqueId())).isNull();
        assertThat(session.isConfirmed(alice.getUniqueId())).isFalse();
        verifyNoInteractions(economy);
    }

    @Test
    @DisplayName("Direct settlement also refuses an expired window before any balance or stake moves")
    void directSettlementAfterDeadlineCancels() throws Exception {
        TradeSession session = open(30);
        session.setItem(alice.getUniqueId(), 0, new ItemStack(Material.DIAMOND, 2));
        expireNow(session);
        service.completeTrade(session);
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(alice.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 2)).isTrue();
        assertThat(bob.getInventory().contains(Material.DIAMOND)).isFalse();
        verifyNoInteractions(economy);
    }

    @Test
    @DisplayName("Completion before the deadline is a positive control and is not later cancelled")
    void completionBeforeDeadlineStaysCompleted() throws Exception {
        TradeSession session = open(30);
        session.setItem(alice.getUniqueId(), 0, new ItemStack(Material.DIAMOND, 2));
        service.confirmTrade(alice);
        service.confirmTrade(bob);
        at(session, seconds(60));
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
        assertThat(bob.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 2)).isTrue();
        assertThat(alice.getInventory().contains(Material.DIAMOND)).isFalse();
        verify(log, never()).logCancelledTrade(any(), any());
    }

    @Test
    @DisplayName("Shutdown empties the scan and stale page-open work cannot reopen the trade")
    void shutdownLeavesNoLiveTimeoutOrTransition() throws Exception {
        TradeSession session = open(30);
        config.setConfirmThreshold(1);
        session.setMoney(alice.getUniqueId(), 10);
        service.confirmTrade(alice);
        service.shutdown();
        at(session, seconds(60));
        server.getScheduler().performOneTick();
        assertThat(service.isTrading(alice.getUniqueId())).isFalse();
        assertThat(service.isOpeningConfirmPage(alice.getUniqueId())).isFalse();
        assertThat(alice.getOpenInventory().getTopInventory()).as("MockBukkit's closed view has no top inventory").isNull();
        verify(log, times(1)).logCancelledTrade(eq(session), anyString());
    }

    @Test
    @DisplayName("Independent sessions expire at their own captured deadlines, even if a cancelled-trade log fails")
    void independentDeadlinesSurviveLogFailure() throws Exception {
        TradeSession first = open(30);
        PlayerMock carol = server.addPlayer("Carol");
        PlayerMock dave = server.addPlayer("Dave");
        limit(60);
        service.startTrade(carol, dave);
        TradeSession second = service.getSession(carol.getUniqueId());
        doThrow(new IllegalStateException("log unavailable")).when(log).logCancelledTrade(eq(first), anyString());
        at(first, seconds(30));
        assertThat(first.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(service.getSession(carol.getUniqueId())).isSameAs(second);
        at(second, seconds(60));
        assertThat(second.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
        assertThat(service.getSession(dave.getUniqueId())).isNull();
    }

    @Test
    @DisplayName("The scheduled entry point really checks elapsed time; already-terminal sessions are not returned twice")
    void scheduledEntryPointAndTerminalGuard() throws Exception {
        TradeSession session = open(30);
        session.setState(TradeSession.TradeState.COMPLETED);
        at(session, seconds(60));
        verifyNoInteractions(log);
        session.setState(TradeSession.TradeState.TRADING);
        expireNow(session);
        TradeService.class.getMethod("cleanupExpiredTradeWindows").invoke(service);
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
    }

    @Test
    @DisplayName("The framework owns one synchronous scanner, not a task per trade")
    void scannerIsSynchronousAndLifecycleOwned() throws Exception {
        Method method = TradeService.class.getMethod("cleanupExpiredTradeWindows");
        Scheduled annotation = method.getAnnotation(Scheduled.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.period()).isEqualTo(20);
        assertThat(annotation.async()).isFalse();
        method.invoke(service); // empty service is harmless
    }
}
