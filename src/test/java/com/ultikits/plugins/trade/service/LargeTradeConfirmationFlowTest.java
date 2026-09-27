package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.gui.TradeGUI;
import com.ultikits.plugins.trade.listener.TradeListener;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The large-trade confirmation flow, end to end: a real {@link TradeService}, a real
 * {@link TradeListener}, the real trade window and confirmation page, and two players whose
 * {@code closeInventory()} and {@code openInventory(...)} fire the close event the listener sees, the
 * way a server does, synchronously. Scheduled tasks are queued and run in the order they were
 * scheduled, as the server's scheduler runs same-tick tasks.
 * <ul>
 *   <li>UltiKits/UltiTrade#23: opening the confirmation page closed the trade window first, and
 *       that close scheduled the auto-cancel ahead of the page, so the trade was cancelled before
 *       the page appeared.</li>
 *   <li>UltiKits/UltiTrade#21: the page's Confirm button marked the player confirmed but never
 *       completed the trade, even once both players had confirmed.</li>
 * </ul>
 */
@DisplayName("Large-trade confirmation flow (UltiKits/UltiTrade#21, #23)")
class LargeTradeConfirmationFlowTest {

    private static final double THRESHOLD = 100.0;

    private TradeService service;
    private TradeListener listener;
    private TradeConfig config;
    private Economy economy;
    private Player player1;
    private Player player2;
    private UUID uuid1;
    private UUID uuid2;

    private final List<Runnable> queue = new ArrayList<>();
    private final Map<Player, Inventory> screens = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        Server server = Bukkit.getServer();
        // Real inventories, so each window knows its own holder.
        doCallRealMethod().when(server).createInventory(any(), anyInt(), anyString());
        BukkitScheduler scheduler = server.getScheduler();
        lenient().when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenAnswer(inv -> {
            queue.add(inv.getArgument(1));
            return mock(BukkitTask.class);
        });
        lenient().when(scheduler.runTask(any(), any(Runnable.class))).thenAnswer(inv -> {
            queue.add(inv.getArgument(1));
            return mock(BukkitTask.class);
        });

        config = UltiTradeTestHelper.createDefaultConfig();
        lenient().when(config.getConfirmThreshold()).thenReturn(THRESHOLD);
        economy = UltiTradeTestHelper.createMockEconomy();
        TradeLogService logService = mock(TradeLogService.class);
        lenient().when(logService.isTradeEnabled(any())).thenReturn(true);

        service = new TradeService();
        UltiTradeTestHelper.setField(service, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(service, "config", config);
        UltiTradeTestHelper.setField(service, "logService", logService);
        UltiTradeTestHelper.setField(service, "economy", economy);

        listener = new TradeListener();
        UltiTradeTestHelper.setField(listener, "tradeService", service);
        UltiTradeTestHelper.setField(listener, "config", config);

        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        player1 = UltiTradeTestHelper.createMockPlayer("Player1", uuid1);
        player2 = UltiTradeTestHelper.createMockPlayer("Player2", uuid2);
        lenient().doReturn(player1).when(server).getPlayer(uuid1);
        lenient().doReturn(player2).when(server).getPlayer(uuid2);
        wireScreen(player1);
        wireScreen(player2);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /**
     * Opening and closing a window behave as on a server: a replaced or closed window fires its close,
     * with the reason Paper gives it -- {@code OPEN_NEW} when another window replaces it,
     * {@code PLUGIN} when a plugin closes it. {@link #pressEscape} is the player's own close.
     */
    private void wireScreen(Player player) {
        lenient().when(player.openInventory(any(Inventory.class))).thenAnswer(inv -> {
            Inventory previous = screens.get(player);
            if (previous != null) {
                fireClose(player, previous, InventoryCloseEvent.Reason.OPEN_NEW);
            }
            screens.put(player, inv.getArgument(0));
            return view(player);
        });
        lenient().doAnswer(inv -> {
            Inventory previous = screens.remove(player);
            if (previous != null) {
                fireClose(player, previous, InventoryCloseEvent.Reason.PLUGIN);
            }
            return null;
        }).when(player).closeInventory();
        lenient().when(player.getOpenInventory()).thenAnswer(inv -> view(player));
    }

    private InventoryView view(Player player) {
        InventoryView view = mock(InventoryView.class);
        Inventory top = screens.get(player);
        if (top == null) {
            top = mock(Inventory.class);
        }
        lenient().when(view.getTopInventory()).thenReturn(top);
        return view;
    }

    private void fireClose(Player player, Inventory inventory, InventoryCloseEvent.Reason reason) {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        lenient().when(event.getInventory()).thenReturn(inventory);
        lenient().when(event.getPlayer()).thenReturn(player);
        lenient().when(event.getReason()).thenReturn(reason);
        listener.onInventoryClose(event);
    }

    /** The player closes their window themselves (Esc). */
    private void pressEscape(Player player) {
        Inventory previous = screens.remove(player);
        if (previous != null) {
            fireClose(player, previous, InventoryCloseEvent.Reason.PLAYER);
        }
    }

    private void runScheduled() {
        int guard = 0;
        while (!queue.isEmpty()) {
            queue.remove(0).run();
            assertThat(++guard).as("scheduled tasks must settle").isLessThan(100);
        }
    }

    private Object holderShownTo(Player player) {
        Inventory top = screens.get(player);
        return top == null ? null : top.getHolder();
    }

    /** A large trade: player 1 offers money above the threshold, player 2 offers an item. */
    private TradeSession startLargeTrade() {
        service.startTrade(player1, player2);
        TradeSession session = service.getSession(uuid1);
        session.setMoney(uuid1, 500.0);
        session.setItem(uuid2, 0, new ItemStack(Material.DIAMOND, 5));
        return session;
    }

    private void clickPage(Player player, int slot) {
        Object holder = holderShownTo(player);
        assertThat(holder).as("the confirmation page is what the player sees").isInstanceOf(TradeConfirmPage.class);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getRawSlot()).thenReturn(slot);
        ((TradeConfirmPage) holder).handleClick(click);
        runScheduled();
    }

    private void confirmThroughThePage(Player player) {
        service.confirmTrade(player);
        runScheduled();
        clickPage(player, TradeConfirmPage.CONFIRM_SLOT);
    }

    @Test
    @DisplayName("confirming a large trade opens the confirmation page and leaves the trade running (UltiKits/UltiTrade#23)")
    void confirmingOpensThePageWithoutCancelling() {
        TradeSession session = startLargeTrade();

        service.confirmTrade(player1);
        runScheduled();

        assertThat(service.getSession(uuid1)).as("the trade is still running").isSameAs(session);
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.TRADING);
        assertThat(holderShownTo(player1)).isInstanceOf(TradeConfirmPage.class);
        verify(player1, never()).sendMessage(contains("交易已取消")); // "交易已取消"
    }

    @Test
    @DisplayName("a large trade completes once both players confirm through the page (UltiKits/UltiTrade#21)")
    void aLargeTradeCompletesThroughThePage() {
        TradeSession session = startLargeTrade();

        confirmThroughThePage(player1);
        assertThat(session.isConfirmed(uuid1)).isTrue();
        assertThat(holderShownTo(player1)).as("back to the trade window after confirming")
                .isInstanceOf(TradeGUI.class);

        confirmThroughThePage(player2);

        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
        assertThat(service.isTrading(uuid1)).isFalse();
        verify(economy).withdrawPlayer(player1, 500.0);
        verify(economy).depositPlayer(eq(player2), eq(500.0));
        verify(player1.getInventory()).addItem(any(ItemStack.class));
    }

    @Test
    @DisplayName("closing the page without choosing returns to the trade window, and the trade keeps running")
    void closingThePageGoesBackToTheTrade() {
        TradeSession session = startLargeTrade();
        service.confirmTrade(player1);
        runScheduled();

        pressEscape(player1);
        runScheduled();

        assertThat(service.getSession(uuid1)).isSameAs(session);
        assertThat(session.isConfirmed(uuid1)).isFalse();
        assertThat(holderShownTo(player1)).isInstanceOf(TradeGUI.class);
    }

    @Test
    @DisplayName("an offer changed while the page is open is not confirmed by the page's button")
    void anOfferChangedBehindThePageIsNotConfirmed() {
        TradeSession session = startLargeTrade();
        service.confirmTrade(player1);
        runScheduled();

        // The other player swaps their offer while player 1 is reading the page.
        session.setItem(uuid2, 0, new ItemStack(Material.DIRT, 1));
        clickPage(player1, TradeConfirmPage.CONFIRM_SLOT);

        assertThat(session.isConfirmed(uuid1)).isFalse();
        assertThat(holderShownTo(player1)).isInstanceOf(TradeGUI.class);
    }

    @Test
    @DisplayName("a reload while the page is open shows the trade window and leaves the trade running")
    void aReloadWhileThePageIsOpenKeepsTheTrade() {
        TradeSession session = startLargeTrade();
        service.confirmTrade(player1);
        runScheduled();
        assertThat(holderShownTo(player1)).isInstanceOf(TradeConfirmPage.class);

        // The reload replaces the page with a trade window; replacing it is not the player's "back".
        service.refreshOpenTradeWindowsAfterReload();
        runScheduled();

        assertThat(service.getSession(uuid1)).as("the trade is still running").isSameAs(session);
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.TRADING);
        assertThat(holderShownTo(player1)).isInstanceOf(TradeGUI.class);
    }

    @Test
    @DisplayName("a cancellation that closes the page does not answer it: no return to the trade window is scheduled")
    void aCancellationClosingThePageDoesNotAnswerIt() {
        TradeSession session = startLargeTrade();
        service.confirmTrade(player1);
        runScheduled();
        assertThat(holderShownTo(player1)).isInstanceOf(TradeConfirmPage.class);

        service.cancelTrade(session, "test");

        // The one task left is the other player's trade-window close check; the page closed by the
        // cancellation adds none.
        assertThat(queue).hasSize(1);
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
    }

    @Test
    @DisplayName("shutting down with the page open returns the other player's stake while the scheduler refuses tasks")
    void shutdownWithThePageOpenReturnsTheStake() {
        startLargeTrade();
        service.confirmTrade(player1);
        runScheduled();
        assertThat(holderShownTo(player1)).isInstanceOf(TradeConfirmPage.class);
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        // The plugin is being disabled: the scheduler refuses new tasks.
        when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
                .thenThrow(new org.bukkit.plugin.IllegalPluginAccessException("disabled"));

        service.shutdown();

        verify(player2.getInventory()).addItem(any(ItemStack.class));
    }

    @Test
    @DisplayName("POSITIVE CONTROL: once the page has opened, closing the trade window still cancels the trade")
    void theTransitionDoesNotOutliveThePage() {
        TradeSession session = startLargeTrade();
        service.confirmTrade(player1);
        runScheduled();
        clickPage(player1, TradeConfirmPage.CANCEL_SLOT);
        assertThat(holderShownTo(player1)).isInstanceOf(TradeGUI.class);

        pressEscape(player1);
        runScheduled();

        assertThat(service.isTrading(uuid1)).isFalse();
        assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
    }
}
