package com.ultikits.plugins.trade.listener;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.gui.TradeGUI;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TradeListener Tests")
class TradeListenerTest {

    private TradeListener listener;
    private TradeService tradeService;
    private TradeConfig config;
    private Player player1;
    private Player player2;
    private UUID uuid1;
    private UUID uuid2;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();

        tradeService = mock(TradeService.class);
        com.ultikits.plugins.trade.i18n.TradeSeams.speak(tradeService, "zh");
        config = UltiTradeTestHelper.createDefaultConfig();

        listener = new TradeListener();
        UltiTradeTestHelper.setField(listener, "tradeService", tradeService);
        UltiTradeTestHelper.setField(listener, "config", config);

        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        player1 = UltiTradeTestHelper.createMockPlayer("Player1", uuid1);
        player2 = UltiTradeTestHelper.createMockPlayer("Player2", uuid2);
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @Nested
    @DisplayName("Shift+Right-Click Trading")
    class ShiftRightClickTrading {

        @Test
        @DisplayName("Should send trade request on shift+right-click")
        void shiftRightClickSendRequest() {
            when(config.isEnableShiftClick()).thenReturn(true);
            when(player1.isSneaking()).thenReturn(true);
            when(player1.hasPermission("ultitrade.use")).thenReturn(true);

            PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player1);
            when(event.getRightClicked()).thenReturn(player2);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);

            listener.onPlayerInteractEntity(event);

            verify(event).setCancelled(true);
            verify(tradeService).sendRequest(player1, player2);
        }

        @Test
        @DisplayName("Should not trigger if shift-click disabled")
        void shiftClickDisabled() {
            when(config.isEnableShiftClick()).thenReturn(false);

            PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player1);

            listener.onPlayerInteractEntity(event);

            verify(tradeService, never()).sendRequest(any(), any());
        }

        @Test
        @DisplayName("Should not trigger if not sneaking")
        void notSneaking() {
            when(config.isEnableShiftClick()).thenReturn(true);
            when(player1.isSneaking()).thenReturn(false);

            PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player1);
            when(event.getRightClicked()).thenReturn(player2);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);

            listener.onPlayerInteractEntity(event);

            verify(tradeService, never()).sendRequest(any(), any());
        }

        @Test
        @DisplayName("Should not trigger if not main hand")
        void notMainHand() {
            when(config.isEnableShiftClick()).thenReturn(true);

            PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
            when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);

            listener.onPlayerInteractEntity(event);

            verify(tradeService, never()).sendRequest(any(), any());
        }

        @Test
        @DisplayName("Should not trigger if no permission")
        void noPermission() {
            when(config.isEnableShiftClick()).thenReturn(true);
            when(player1.isSneaking()).thenReturn(true);
            when(player1.hasPermission("ultitrade.use")).thenReturn(false);

            PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player1);
            when(event.getRightClicked()).thenReturn(player2);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);

            listener.onPlayerInteractEntity(event);

            verify(tradeService, never()).sendRequest(any(), any());
        }

        @Test
        @DisplayName("Should not trigger for self-trade")
        void selfTrade() {
            when(config.isEnableShiftClick()).thenReturn(true);
            when(player1.isSneaking()).thenReturn(true);

            PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player1);
            when(event.getRightClicked()).thenReturn(player1);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);

            listener.onPlayerInteractEntity(event);

            verify(tradeService, never()).sendRequest(any(), any());
        }

        @Test
        @DisplayName("Should not trigger if right-clicked entity is not a Player")
        void nonPlayerEntity() {
            when(config.isEnableShiftClick()).thenReturn(true);
            when(player1.isSneaking()).thenReturn(true);

            Entity entity = mock(Entity.class); // Not a Player
            PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
            when(event.getPlayer()).thenReturn(player1);
            when(event.getRightClicked()).thenReturn(entity);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);

            listener.onPlayerInteractEntity(event);

            verify(tradeService, never()).sendRequest(any(), any());
        }
    }

    @Nested
    @DisplayName("Player Quit Handling")
    class PlayerQuitHandling {

        @Test
        @DisplayName("Should cancel trade on quit")
        void cancelTradeOnQuit() {
            when(tradeService.isTrading(uuid1)).thenReturn(true);

            PlayerQuitEvent event = new PlayerQuitEvent(player1, "Quit message");

            listener.onPlayerQuit(event);

            verify(tradeService).cancelTrade(player1);
        }

        @Test
        @DisplayName("Should not cancel if not trading")
        void noTradeOnQuit() {
            when(tradeService.isTrading(uuid1)).thenReturn(false);

            PlayerQuitEvent event = new PlayerQuitEvent(player1, "Quit message");

            listener.onPlayerQuit(event);

            verify(tradeService, never()).cancelTrade(any());
        }

        @Test
        @DisplayName("Should remove from waiting for input on quit")
        void removeFromWaitingOnQuit() throws Exception {
            Map<UUID, ?> waitingForInput = UltiTradeTestHelper.getField(listener, "waitingForInput");
            // Add player to waiting list - we need to use the enum via reflection
            Class<?> inputTypeClass = Class.forName("com.ultikits.plugins.trade.listener.TradeListener$InputType");
            Object moneyType = inputTypeClass.getEnumConstants()[0]; // MONEY
            @SuppressWarnings("unchecked")
            Map<UUID, Object> typedMap = (Map<UUID, Object>) waitingForInput;
            typedMap.put(uuid1, moneyType);

            when(tradeService.isTrading(uuid1)).thenReturn(false);

            PlayerQuitEvent event = new PlayerQuitEvent(player1, "Quit message");
            listener.onPlayerQuit(event);

            assertThat(waitingForInput).doesNotContainKey(uuid1);
        }
    }

    /**
     * The click event here is a Mockito mock, so these cases prove only that every click is
     * cancelled; that a cancelled click moves no item, and that the place and remove actions move
     * exactly the one offered item, is proven by {@code placeItemClearsCursorAndStoresOneCopy} and the
     * session item tests.
     */
    @Nested
    @DisplayName("display items in the trade window can never be taken (UltiKits/UltiTrade#25)")
    class DisplayItemsCannotBeTaken {

        private TradeGUI gui;
        private TradeSession session;

        @BeforeEach
        void openWindow() {
            gui = mock(TradeGUI.class);
            session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);
            when(gui.isMoneySlot(TradeGUI.YOUR_MONEY_SLOT)).thenReturn(true);
            when(gui.isExpSlot(TradeGUI.YOUR_EXP_SLOT)).thenReturn(true);
            when(gui.isYourSlot(TradeGUI.YOUR_SLOTS[0])).thenReturn(true);
            when(gui.getItemIndex(TradeGUI.YOUR_SLOTS[0])).thenReturn(0);
        }

        private InventoryClickEvent click(int rawSlot, ClickType type, ItemStack current) {
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(gui);
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(top);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(rawSlot);
            when(event.getClick()).thenReturn(type);
            when(event.getCurrentItem()).thenReturn(current);
            return event;
        }

        private void assertCancelledAndNothingMoved(InventoryClickEvent event) {
            verify(event).setCancelled(true);
            verify(event, never()).setCancelled(false);
            verify(player1.getInventory(), never()).addItem(any(ItemStack.class));
            assertThat(session.getPlayerItems(uuid1)).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ClickType.class, names = {"LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DROP", "CONTROL_DROP", "DOUBLE_CLICK", "SWAP_OFFHAND", "MIDDLE"})
        @DisplayName("money slot with money trading off: the barrier cannot be taken")
        void moneySlotFeatureOff(ClickType type) {
            when(tradeService.hasEconomy()).thenReturn(false);
            InventoryClickEvent event = click(TradeGUI.YOUR_MONEY_SLOT, type, new ItemStack(Material.BARRIER));

            listener.onInventoryClick(event);

            assertCancelledAndNothingMoved(event);
            verify(player1, never()).closeInventory();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ClickType.class, names = {"LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DROP", "CONTROL_DROP", "DOUBLE_CLICK", "SWAP_OFFHAND", "MIDDLE"})
        @DisplayName("money slot with money trading on: the gold nugget cannot be taken and the prompt opens")
        void moneySlotFeatureOn(ClickType type) {
            when(tradeService.hasEconomy()).thenReturn(true);
            InventoryClickEvent event = click(TradeGUI.YOUR_MONEY_SLOT, type, new ItemStack(Material.GOLD_NUGGET));

            listener.onInventoryClick(event);

            assertCancelledAndNothingMoved(event);
            verify(player1).closeInventory();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ClickType.class, names = {"LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DROP", "CONTROL_DROP", "DOUBLE_CLICK", "SWAP_OFFHAND", "MIDDLE"})
        @DisplayName("experience slot with experience trading off: the barrier cannot be taken")
        void expSlotFeatureOff(ClickType type) {
            when(config.isEnableExpTrade()).thenReturn(false);
            InventoryClickEvent event = click(TradeGUI.YOUR_EXP_SLOT, type, new ItemStack(Material.BARRIER));

            listener.onInventoryClick(event);

            assertCancelledAndNothingMoved(event);
            verify(player1, never()).closeInventory();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ClickType.class, names = {"LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DROP", "CONTROL_DROP", "DOUBLE_CLICK", "SWAP_OFFHAND", "MIDDLE"})
        @DisplayName("experience slot with experience trading on: the bottle cannot be taken and the prompt opens")
        void expSlotFeatureOn(ClickType type) {
            when(config.isEnableExpTrade()).thenReturn(true);
            InventoryClickEvent event = click(TradeGUI.YOUR_EXP_SLOT, type, new ItemStack(Material.EXPERIENCE_BOTTLE));

            listener.onInventoryClick(event);

            assertCancelledAndNothingMoved(event);
            verify(player1).closeInventory();
        }

        @Test
        @DisplayName("a click outside the window (raw slot -999) while the trade window is open is cancelled too")
        void clickOutsideWindowIsCancelled() {
            InventoryClickEvent event = click(-999, ClickType.LEFT, null);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("placing an item from the cursor stores one copy in the session and clears the cursor, so the item is not in both places")
        void placeItemClearsCursorAndStoresOneCopy() {
            ItemStack offered = new ItemStack(Material.DIAMOND, 4);
            InventoryClickEvent event = click(TradeGUI.YOUR_SLOTS[0], ClickType.LEFT, new ItemStack(Material.LIME_STAINED_GLASS_PANE));
            when(event.getCursor()).thenReturn(offered);
            InventoryView view = mock(InventoryView.class);
            when(event.getView()).thenReturn(view);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            verify(view).setCursor(null);
            assertThat(session.getPlayerItems(uuid1)).hasSize(1);
            ItemStack stored = session.getPlayerItems(uuid1).get(0);
            assertThat(stored.getType()).isEqualTo(Material.DIAMOND);
            assertThat(stored.getAmount()).isEqualTo(4);
            verify(player1.getInventory(), never()).addItem(any(ItemStack.class));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = ClickType.class, names = {"LEFT", "RIGHT", "SHIFT_LEFT", "SHIFT_RIGHT", "NUMBER_KEY", "DROP", "CONTROL_DROP", "DOUBLE_CLICK", "SWAP_OFFHAND", "MIDDLE"})
        @DisplayName("an empty own item slot clicked with an empty cursor: its placeholder glass pane cannot be taken")
        void emptyOwnSlotPlaceholder(ClickType type) {
            InventoryClickEvent event = click(TradeGUI.YOUR_SLOTS[0], type, new ItemStack(Material.LIME_STAINED_GLASS_PANE));
            when(event.getCursor()).thenReturn(null);

            listener.onInventoryClick(event);

            assertCancelledAndNothingMoved(event);
        }
    }

    /**
     * An item taken back out of the trade window has to end up somewhere the acting player can
     * recover it. These cases run the listener against a REAL {@link TradeService}, not the mock the
     * outer class injects, so they observe the delivery itself — the inventory call and the drop —
     * rather than a delegation to a stub that would report success while nothing was delivered
     * (UltiKits/UltiTrade#20).
     */
    @Nested
    @DisplayName("removing a placed item never destroys it (UltiKits/UltiTrade#20)")
    class RemovedItemIsNeverDestroyed {

        private TradeGUI gui;
        private TradeSession session;
        private ItemStack offered;

        @BeforeEach
        void openWindowWithOnePlacedItem() throws Exception {
            TradeService realService = new TradeService();
            // The module plugin, whose language file the service reads (UltiKits/UltiTrade#16)
            UltiTradeTestHelper.setField(realService, "plugin", UltiTradeTestHelper.getMockPlugin());
            UltiTradeTestHelper.setField(realService, "config", config);
            UltiTradeTestHelper.setField(realService, "logService", mock(TradeLogService.class));
            UltiTradeTestHelper.setField(listener, "tradeService", realService);

            session = new TradeSession(player1, player2);
            offered = new ItemStack(Material.DIAMOND, 64);
            session.setItem(uuid1, 0, offered);

            gui = mock(TradeGUI.class);
            when(gui.getSession()).thenReturn(session);
            when(gui.isYourSlot(TradeGUI.YOUR_SLOTS[0])).thenReturn(true);
            when(gui.getItemIndex(TradeGUI.YOUR_SLOTS[0])).thenReturn(0);
        }

        /**
         * Click the slot that already holds the placed item, which is the listener's remove-item
         * branch: the clicked slot's current item is the real offered item, not a glass pane.
         */
        private InventoryClickEvent clickPlacedItem() {
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(gui);
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(top);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.YOUR_SLOTS[0]);
            when(event.getCurrentItem()).thenReturn(offered);
            return event;
        }

        /**
         * Proves the remove-item branch actually ran, so the drop assertions below cannot pass
         * vacuously: the offer left the session and the inventory was asked to take the item.
         */
        private void assertRemovalBranchRan() {
            assertThat(session.getPlayerItems(uuid1)).doesNotContainKey(0);
            verify(player1.getInventory()).addItem(UltiTradeTestHelper.deliveredCopyOf(offered));
        }

        @Test
        @DisplayName("inventory completely full: the item drops at the acting player's feet instead of being destroyed")
        void fullInventoryDropsTheRemovedItem() {
            HashMap<Integer, ItemStack> overflow = new HashMap<>();
            overflow.put(0, offered);
            // The delivery hands over a copy (UltiKits/UltiTrade#37), so the stub matches any stack; what was
            // delivered is asserted separately.
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(overflow);

            listener.onInventoryClick(clickPlacedItem());

            assertRemovalBranchRan();
            verify(player1.getWorld()).dropItemNaturally(player1.getLocation(), offered);
        }

        @Test
        @DisplayName("inventory has room: the item goes back into the inventory and nothing is dropped")
        void roomInInventoryDropsNothing() {
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            listener.onInventoryClick(clickPlacedItem());

            assertRemovalBranchRan();
            verify(player1.getWorld(), never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        }

        @Test
        @DisplayName("the drop loop covers the whole leftover map, not just its first entry")
        void theWholeLeftoverMapIsDropped() {
            // A single-stack addItem can only ever key its leftover map at 0, so this two-entry map is a
            // state the platform does not produce; what it pins is that the loop iterates every value
            // rather than reading one index.
            ItemStack secondStack = new ItemStack(Material.GOLD_INGOT, 16);
            HashMap<Integer, ItemStack> overflow = new HashMap<>();
            overflow.put(0, offered);
            overflow.put(1, secondStack);
            // The delivery hands over a copy (UltiKits/UltiTrade#37), so the stub matches any stack; what was
            // delivered is asserted separately.
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(overflow);

            listener.onInventoryClick(clickPlacedItem());

            assertRemovalBranchRan();
            verify(player1.getWorld()).dropItemNaturally(player1.getLocation(), offered);
            verify(player1.getWorld()).dropItemNaturally(player1.getLocation(), secondStack);
        }
    }

    /**
     * Which branch a click on one of the acting player's own item slots takes must be decided by the
     * session — the only authority on what that player has offered — and never by a property the
     * player controls. Reading the rendered item's material made a stored stained glass pane
     * indistinguishable from the empty-slot placeholder, so placing another item over it destroyed
     * it. These cases run against a REAL {@link TradeService} so the hand-back is observed rather
     * than stubbed (UltiKits/UltiTrade#31).
     */
    @Nested
    @DisplayName("a stored offer is never overwritten, whatever it looks like (UltiKits/UltiTrade#31)")
    class StoredOfferIsNeverOverwritten {

        private TradeGUI gui;
        private TradeSession session;

        @BeforeEach
        void openWindow() throws Exception {
            TradeService realService = new TradeService();
            // The module plugin, whose language file the service reads (UltiKits/UltiTrade#16)
            UltiTradeTestHelper.setField(realService, "plugin", UltiTradeTestHelper.getMockPlugin());
            UltiTradeTestHelper.setField(realService, "config", config);
            UltiTradeTestHelper.setField(realService, "logService", mock(TradeLogService.class));
            UltiTradeTestHelper.setField(listener, "tradeService", realService);

            session = new TradeSession(player1, player2);

            gui = mock(TradeGUI.class);
            when(gui.getSession()).thenReturn(session);
            when(gui.isYourSlot(TradeGUI.YOUR_SLOTS[0])).thenReturn(true);
            when(gui.getItemIndex(TradeGUI.YOUR_SLOTS[0])).thenReturn(0);
        }

        /**
         * @param rendered what the slot currently shows — the placeholder pane for an empty slot, or
         *                 the offered item itself for an occupied one, exactly as {@code TradeGUI#update}
         *                 writes it
         * @param cursor   what the acting player is holding, or {@code null} for an empty cursor
         */
        private InventoryView clickOwnSlot(ItemStack rendered, ItemStack cursor) {
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(gui);
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(top);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.YOUR_SLOTS[0]);
            // Stubbed to present a realistic event: the handler must not consult it, which
            // theRenderDoesNotDecide below proves by rendering a placeholder over an occupied slot.
            when(event.getCurrentItem()).thenReturn(rendered);
            when(event.getCursor()).thenReturn(cursor);
            InventoryView view = mock(InventoryView.class);
            when(event.getView()).thenReturn(view);
            listener.onInventoryClick(event);
            return view;
        }

        @Test
        @DisplayName("placing an item over a stored stained glass pane hands the pane back instead of destroying it")
        void storedGlassPaneIsHandedBack() {
            ItemStack storedPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 7);
            session.setItem(uuid1, 0, storedPane);
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            InventoryView view = clickOwnSlot(storedPane, new ItemStack(Material.DIAMOND, 1));

            assertThat(session.getPlayerItems(uuid1))
                    .as("the item on the cursor must take the slot")
                    .containsKey(0);
            assertThat(session.getPlayerItems(uuid1).get(0).getType()).isEqualTo(Material.DIAMOND);
            verify(player1.getInventory()).addItem(UltiTradeTestHelper.deliveredCopyOf(storedPane));
            verify(view).setCursor(null);
            verify(player1.getWorld(), never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        }

        @Test
        @DisplayName("the same click with a full inventory drops the stored pane at the acting player's feet")
        void storedGlassPaneDropsWhenInventoryFull() {
            ItemStack storedPane = new ItemStack(Material.LIME_STAINED_GLASS_PANE, 7);
            session.setItem(uuid1, 0, storedPane);
            HashMap<Integer, ItemStack> overflow = new HashMap<>();
            overflow.put(0, storedPane);
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(overflow);

            clickOwnSlot(storedPane, new ItemStack(Material.DIAMOND, 1));

            assertThat(session.getPlayerItems(uuid1))
                    .as("the item on the cursor must take the slot")
                    .containsKey(0);
            assertThat(session.getPlayerItems(uuid1).get(0).getType()).isEqualTo(Material.DIAMOND);
            verify(player1.getWorld()).dropItemNaturally(player1.getLocation(), storedPane);
        }

        @Test
        @DisplayName("a stored stained glass pane can be taken back out with an empty cursor, like any other offer")
        void storedGlassPaneCanBeTakenBack() {
            ItemStack storedPane = new ItemStack(Material.CYAN_STAINED_GLASS_PANE, 3);
            session.setItem(uuid1, 0, storedPane);
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            InventoryView view = clickOwnSlot(storedPane, null);

            assertThat(session.getPlayerItems(uuid1)).doesNotContainKey(0);
            verify(player1.getInventory()).addItem(UltiTradeTestHelper.deliveredCopyOf(storedPane));
            verify(view, never()).setCursor(any());
        }

        @Test
        @DisplayName("placing an item over an ordinary stored offer swaps: the new item takes the slot, the old one comes back")
        void ordinaryStoredOfferIsSwappedNotLost() {
            ItemStack storedDiamond = new ItemStack(Material.DIAMOND, 2);
            session.setItem(uuid1, 0, storedDiamond);
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            InventoryView view = clickOwnSlot(storedDiamond, new ItemStack(Material.GOLD_INGOT, 5));

            assertThat(session.getPlayerItems(uuid1))
                    .as("the item on the cursor must take the slot")
                    .containsKey(0);
            ItemStack nowOffered = session.getPlayerItems(uuid1).get(0);
            assertThat(nowOffered.getType()).isEqualTo(Material.GOLD_INGOT);
            assertThat(nowOffered.getAmount()).isEqualTo(5);
            verify(player1.getInventory()).addItem(UltiTradeTestHelper.deliveredCopyOf(storedDiamond));
            verify(view).setCursor(null);
        }

        @Test
        @DisplayName("the rendered item does not decide: a placeholder drawn over an occupied slot still hands the offer back")
        void theRenderDoesNotDecide() {
            ItemStack storedDiamond = new ItemStack(Material.DIAMOND, 2);
            session.setItem(uuid1, 0, storedDiamond);
            when(player1.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            // The real state behind UltiKits/UltiTrade#35: a window opened without rendering the session
            // draws the empty-slot placeholder while the session holds a real offer.
            InventoryView view = clickOwnSlot(new ItemStack(Material.LIME_STAINED_GLASS_PANE), null);

            assertThat(session.getPlayerItems(uuid1)).doesNotContainKey(0);
            verify(player1.getInventory()).addItem(UltiTradeTestHelper.deliveredCopyOf(storedDiamond));
            verify(view, never()).setCursor(any());
        }

        @Test
        @DisplayName("control: placing into a genuinely empty slot still stores the item and hands nothing back")
        void genuinelyEmptySlotStillAcceptsAnItem() {
            InventoryView view = clickOwnSlot(
                    new ItemStack(Material.LIME_STAINED_GLASS_PANE),
                    new ItemStack(Material.DIAMOND, 4));

            assertThat(session.getPlayerItems(uuid1))
                    .as("the item on the cursor must take the slot")
                    .containsKey(0);
            ItemStack nowOffered = session.getPlayerItems(uuid1).get(0);
            assertThat(nowOffered.getType()).isEqualTo(Material.DIAMOND);
            assertThat(nowOffered.getAmount()).isEqualTo(4);
            verify(view).setCursor(null);
            verify(player1.getInventory(), never()).addItem(any(ItemStack.class));
            verify(player1.getWorld(), never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        }
    }

    /**
     * Only a participant's click may move a participant's offer. {@code TradeGUI}'s slot test carries no
     * perspective, and {@code TradeSession#getPlayerItems} treats everyone who is not player 1 as
     * player 2, so without a participant check a third viewer of a live trade window is handed player
     * 2's stake. The second case below is the mirror of the first, acting as player 2, which is the arm
     * of that dispatch the rest of the suite never exercises (UltiKits/UltiTrade#38).
     */
    @Nested
    @DisplayName("only a participant's click moves a participant's offer (UltiKits/UltiTrade#38)")
    class OnlyParticipantsMayClick {

        private TradeGUI gui;
        private TradeSession session;

        @BeforeEach
        void openWindow() throws Exception {
            TradeService realService = new TradeService();
            // The module plugin, whose language file the service reads (UltiKits/UltiTrade#16)
            UltiTradeTestHelper.setField(realService, "plugin", UltiTradeTestHelper.getMockPlugin());
            UltiTradeTestHelper.setField(realService, "config", config);
            UltiTradeTestHelper.setField(realService, "logService", mock(TradeLogService.class));
            UltiTradeTestHelper.setField(listener, "tradeService", realService);

            session = new TradeSession(player1, player2);
            gui = mock(TradeGUI.class);
            when(gui.getSession()).thenReturn(session);
            when(gui.isYourSlot(TradeGUI.YOUR_SLOTS[0])).thenReturn(true);
            when(gui.getItemIndex(TradeGUI.YOUR_SLOTS[0])).thenReturn(0);
        }

        private void click(Player clicker, ItemStack rendered, ItemStack cursor) {
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(gui);
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(top);
            when(event.getWhoClicked()).thenReturn(clicker);
            when(event.getRawSlot()).thenReturn(TradeGUI.YOUR_SLOTS[0]);
            when(event.getCurrentItem()).thenReturn(rendered);
            when(event.getCursor()).thenReturn(cursor);
            when(event.getView()).thenReturn(mock(InventoryView.class));
            listener.onInventoryClick(event);
        }

        @Test
        @DisplayName("a third player viewing the window receives nothing and changes neither side's offer")
        void aThirdViewerCannotTakeAnything() {
            ItemStack player2Stake = new ItemStack(Material.DIAMOND, 8);
            session.setItem(uuid2, 0, player2Stake);
            UUID uuid3 = UUID.randomUUID();
            Player player3 = UltiTradeTestHelper.createMockPlayer("Onlooker", uuid3);
            when(player3.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            click(player3, player2Stake, null);

            assertThat(session.getPlayerItems(uuid2)).containsKey(0);
            assertThat(session.getPlayerItems(uuid2).get(0).getAmount()).isEqualTo(8);
            assertThat(session.getPlayerItems(uuid1)).isEmpty();
            verify(player3.getInventory(), never()).addItem(any(ItemStack.class));
            verify(player3.getWorld(), never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
        }

        @Test
        @DisplayName("player 2 clicking their own occupied slot gets their own offer back")
        void playerTwoCanTakeTheirOwnOfferBack() {
            ItemStack player2Stake = new ItemStack(Material.DIAMOND, 8);
            session.setItem(uuid2, 0, player2Stake);
            when(player2.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            click(player2, player2Stake, null);

            assertThat(session.getPlayerItems(uuid2)).doesNotContainKey(0);
            verify(player2.getInventory()).addItem(UltiTradeTestHelper.deliveredCopyOf(player2Stake));
        }
    }

    @Nested
    @DisplayName("Inventory Click Handling")
    class InventoryClickHandling {

        @Test
        @DisplayName("Should handle confirm button click")
        void confirmButtonClick() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);
            when(gui.getInventory()).thenReturn(mock(Inventory.class));

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.CONFIRM_SLOT);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            verify(tradeService).confirmTrade(player1);
        }

        @Test
        @DisplayName("Should handle cancel button click")
        void cancelButtonClick() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.CANCEL_SLOT);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            verify(tradeService).cancelTrade(player1);
        }

        @Test
        @DisplayName("Should cancel unconfirm on confirm button click")
        void unconfirmButtonClick() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            session.setConfirmed(uuid1, true);
            when(gui.getSession()).thenReturn(session);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.CONFIRM_SLOT);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            verify(tradeService).cancelConfirmation(player1);
        }

        @Test
        @DisplayName("Should handle click outside inventory")
        void clickOutside() {
            TradeGUI gui = mock(TradeGUI.class);
            // A real InventoryClickEvent always carries its clicker, and the handler is about to read
            // who it is in order to refuse a non-participant (UltiKits/UltiTrade#38).
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(100); // Outside inventory

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should ignore non-TradeGUI inventory clicks")
        void nonTradeGuiClick() {
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(null);

            listener.onInventoryClick(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("Should block clicks on their slots")
        void blockTheirSlots() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.THEIR_SLOTS[0]); // First "their" slot

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should block clicks on separator slots")
        void blockSeparatorSlots() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.SEPARATOR_SLOTS[0]); // First separator slot

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should block clicks on status slots")
        void blockStatusSlots() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);
            when(gui.isMoneySlot(anyInt())).thenReturn(false);
            when(gui.isExpSlot(anyInt())).thenReturn(false);
            when(gui.isYourSlot(anyInt())).thenReturn(false);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.YOUR_STATUS_SLOT);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should handle TradeConfirmPage clicks")
        void confirmPageClick() {
            TradeConfirmPage confirmPage = mock(TradeConfirmPage.class);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(confirmPage);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            verify(confirmPage).handleClick(event);
        }

        @Test
        @DisplayName("Should handle money slot click with economy")
        void moneySlotClick() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);
            when(gui.isMoneySlot(TradeGUI.YOUR_MONEY_SLOT)).thenReturn(true);
            when(tradeService.hasEconomy()).thenReturn(true);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.YOUR_MONEY_SLOT);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            verify(player1).closeInventory();
            verify(player1).sendMessage(contains("\u91D1\u5E01\u6570\u91CF")); // "金币数量"
        }

        @Test
        @DisplayName("Should handle experience slot click")
        void expSlotClick() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);
            when(gui.isMoneySlot(anyInt())).thenReturn(false);
            when(gui.isExpSlot(TradeGUI.YOUR_EXP_SLOT)).thenReturn(true);
            when(config.isEnableExpTrade()).thenReturn(true);
            when(tradeService.getTotalExperience(player1)).thenReturn(500);

            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(TradeGUI.YOUR_EXP_SLOT);

            listener.onInventoryClick(event);

            verify(event).setCancelled(true);
            verify(player1).closeInventory();
            verify(player1).sendMessage(contains("\u7ECF\u9A8C\u503C")); // "经验值"
        }
    }

    @Nested
    @DisplayName("Inventory Drag Handling")
    class InventoryDragHandling {

        /**
         * A real {@link InventoryDragEvent} always names at least two raw slots: vanilla re-dispatches
         * a one-slot quick-craft as an ordinary {@code PICKUP} click and constructs no drag event at
         * all. These two cases used to present a drag with no slots, which is a state the platform
         * does not produce.
         */
        @Test
        @DisplayName("Should cancel drag on TradeGUI")
        void cancelDragOnTradeGUI() {
            TradeGUI gui = mock(TradeGUI.class);

            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getRawSlots()).thenReturn(new LinkedHashSet<>(Arrays.asList(
                    TradeGUI.YOUR_SLOTS[0], TradeGUI.YOUR_SLOTS[1])));

            listener.onInventoryDrag(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should cancel drag on TradeConfirmPage")
        void cancelDragOnConfirmPage() {
            TradeConfirmPage confirmPage = mock(TradeConfirmPage.class);

            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(confirmPage);
            when(event.getRawSlots()).thenReturn(new LinkedHashSet<>(Arrays.asList(
                    TradeConfirmPage.YOUR_ITEMS_START, TradeConfirmPage.YOUR_ITEMS_START + 1)));

            listener.onInventoryDrag(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("Should not cancel drag on non-trade inventory")
        void noCancelDragOnOtherInventory() {
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(null);

            listener.onInventoryDrag(event);

            verify(event, never()).setCancelled(true);
        }
    }

    @Nested
    @DisplayName("Inventory Close Handling")
    class InventoryCloseHandling {

        @Test
        @DisplayName("Should cancel trade on close")
        void cancelTradeOnClose() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);
            when(tradeService.getSession(uuid1)).thenReturn(session);

            InventoryCloseEvent event = mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getPlayer()).thenReturn(player1);

            listener.onInventoryClose(event);

            // Verify scheduled task (implementation specific)
            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.TRADING);
        }

        @Test
        @DisplayName("Should not cancel trade on TradeConfirmPage close")
        void dontCancelOnConfirmPageClose() {
            TradeConfirmPage confirmPage = mock(TradeConfirmPage.class);

            InventoryCloseEvent event = mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(confirmPage);
            when(event.getPlayer()).thenReturn(player1);

            listener.onInventoryClose(event);

            // Should return early without calling cancelTrade
            verify(tradeService, never()).cancelTrade(any(Player.class));
        }

        @Test
        @DisplayName("Should not cancel if not a TradeGUI holder")
        void notTradeGUIHolder() {
            InventoryCloseEvent event = mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(null);

            listener.onInventoryClose(event);

            verify(tradeService, never()).getSession(any());
        }

        @Test
        @DisplayName("Should not cancel trade when waiting for input")
        void dontCancelWhenWaitingForInput() throws Exception {
            Map<UUID, ?> waitingForInput = UltiTradeTestHelper.getField(listener, "waitingForInput");
            Class<?> inputTypeClass = Class.forName("com.ultikits.plugins.trade.listener.TradeListener$InputType");
            Object moneyType = inputTypeClass.getEnumConstants()[0];
            @SuppressWarnings("unchecked")
            Map<UUID, Object> typedMap = (Map<UUID, Object>) waitingForInput;
            typedMap.put(uuid1, moneyType);

            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            when(gui.getSession()).thenReturn(session);
            when(tradeService.getSession(uuid1)).thenReturn(session);

            InventoryCloseEvent event = mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getPlayer()).thenReturn(player1);

            listener.onInventoryClose(event);

            // Should not schedule cancel because player is waiting for input
            verify(tradeService, never()).cancelTrade(any(Player.class));
        }

        @Test
        @DisplayName("Should not cancel if session is not in TRADING state")
        void notInTradingState() {
            TradeGUI gui = mock(TradeGUI.class);
            TradeSession session = new TradeSession(player1, player2);
            session.setState(TradeSession.TradeState.COMPLETED);
            when(gui.getSession()).thenReturn(session);
            when(tradeService.getSession(uuid1)).thenReturn(session);

            InventoryCloseEvent event = mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getPlayer()).thenReturn(player1);

            listener.onInventoryClose(event);

            // Session is completed, so no cancel should be scheduled
            verify(tradeService, never()).cancelTrade(any(Player.class));
        }

        @Test
        @DisplayName("Should not cancel if no session found for player")
        void noSessionFound() {
            TradeGUI gui = mock(TradeGUI.class);

            when(tradeService.getSession(uuid1)).thenReturn(null);

            InventoryCloseEvent event = mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(mock(Inventory.class));
            when(event.getInventory().getHolder()).thenReturn(gui);
            when(event.getPlayer()).thenReturn(player1);

            listener.onInventoryClose(event);

            verify(tradeService, never()).cancelTrade(any(Player.class));
        }
    }

    @Nested
    @DisplayName("Chat Input Handling")
    class ChatInputHandling {

        @Test
        @DisplayName("Should ignore chat if not waiting for input")
        void ignoreChatNotWaiting() {
            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "hello", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("Should handle cancel input")
        void handleCancelInput() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "cancel", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player1).sendMessage(contains("\u53D6\u6D88\u8F93\u5165")); // "取消输入"
        }

        @Test
        @DisplayName("Should handle negative value input")
        void handleNegativeValue() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "-100", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player1).sendMessage(contains("\u4E0D\u80FD\u4E3A\u8D1F\u6570")); // "不能为负数"
        }

        @Test
        @DisplayName("Should handle invalid number input")
        void handleInvalidNumber() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "not_a_number", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player1).sendMessage(contains("\u65E0\u6548\u7684\u6570\u503C")); // "无效的数值"
        }

        @Test
        @DisplayName("Should handle valid money input")
        void handleValidMoneyInput() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            TradeSession session = new TradeSession(player1, player2);
            when(tradeService.getSession(uuid1)).thenReturn(session);
            when(tradeService.hasEconomy()).thenReturn(true);
            net.milkbowl.vault.economy.Economy mockEconomy = UltiTradeTestHelper.createMockEconomy();
            when(tradeService.getEconomy()).thenReturn(mockEconomy);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            assertThat(session.getPlayerMoney(uuid1)).isEqualTo(500.0);
            verify(player1).sendMessage(contains("\u91D1\u5E01")); // "金币"
        }

        @Test
        @DisplayName("Should handle valid experience input")
        void handleValidExpInput() throws Exception {
            addToWaitingForInput(uuid1, 1); // EXPERIENCE

            TradeSession session = new TradeSession(player1, player2);
            when(tradeService.getSession(uuid1)).thenReturn(session);
            when(tradeService.getTotalExperience(player1)).thenReturn(1000);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            assertThat(session.getPlayerExp(uuid1)).isEqualTo(500);
            verify(player1).sendMessage(contains("\u7ECF\u9A8C")); // "经验"
        }

        @Test
        @DisplayName("Should reject money input exceeding balance")
        void rejectInsufficientBalance() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            TradeSession session = new TradeSession(player1, player2);
            when(tradeService.getSession(uuid1)).thenReturn(session);
            when(tradeService.hasEconomy()).thenReturn(true);
            net.milkbowl.vault.economy.Economy mockEconomy = mock(net.milkbowl.vault.economy.Economy.class);
            when(mockEconomy.getBalance(player1)).thenReturn(100.0);
            when(tradeService.getEconomy()).thenReturn(mockEconomy);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player1).sendMessage(contains("\u4F59\u989D\u4E0D\u8DB3")); // "余额不足"
        }

        @Test
        @DisplayName("a reload that drops the provider while the money prompt is answered does not throw, keeps the amount and reopens the GUI (UltiKits/UltiTrade#26)")
        void moneyInputAfterProviderDroppedMidRead() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            TradeSession session = new TradeSession(player1, player2);
            when(tradeService.getSession(uuid1)).thenReturn(session);
            when(tradeService.isTrading(uuid1)).thenReturn(true);
            // The race: the availability check still sees the provider, the second read does not.
            when(tradeService.hasEconomy()).thenReturn(true);
            when(tradeService.getEconomy()).thenReturn(null);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            assertThatCode(() -> listener.onPlayerChat(event)).doesNotThrowAnyException();

            assertThat(event.isCancelled()).isTrue();
            assertThat(session.getPlayerMoney(uuid1)).isEqualTo(0.0);
            verify(player1).sendMessage(contains("\u91D1\u5E01\u4EA4\u6613\u5F53\u524D\u4E0D\u53EF\u7528")); // "金币交易当前不可用"
            verify(org.bukkit.Bukkit.getServer().getScheduler()).runTask(any(), any(Runnable.class));
        }

        @Test
        @DisplayName("provider still held but enable-money-trade already false in memory: the money amount is refused (UltiKits/UltiTrade#26)")
        void moneyInputRefusedWhenMoneyTradeOffButProviderHeld() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            TradeSession session = new TradeSession(player1, player2);
            when(tradeService.getSession(uuid1)).thenReturn(session);
            net.milkbowl.vault.economy.Economy heldEconomy = UltiTradeTestHelper.createMockEconomy();
            when(tradeService.getEconomy()).thenReturn(heldEconomy);
            when(config.isEnableMoneyTrade()).thenReturn(false);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(session.getPlayerMoney(uuid1)).isEqualTo(0.0);
            verify(player1).sendMessage(contains("\u91D1\u5E01\u4EA4\u6613\u5F53\u524D\u4E0D\u53EF\u7528")); // "金币交易当前不可用"
        }

        @Test
        @DisplayName("experience prompt answered after a reload turned enable-exp-trade off: the amount is refused and the GUI reopens (UltiKits/UltiTrade#26)")
        void expInputRefusedWhenExpTradeOff() throws Exception {
            addToWaitingForInput(uuid1, 1); // EXPERIENCE

            TradeSession session = new TradeSession(player1, player2);
            when(tradeService.getSession(uuid1)).thenReturn(session);
            when(tradeService.getTotalExperience(player1)).thenReturn(1000);
            when(config.isEnableExpTrade()).thenReturn(false);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            assertThat(session.getPlayerExp(uuid1)).isEqualTo(0);
            verify(player1).sendMessage(contains("\u7ECF\u9A8C\u4EA4\u6613\u5F53\u524D\u4E0D\u53EF\u7528")); // "经验交易当前不可用"
            verify(org.bukkit.Bukkit.getServer().getScheduler()).runTask(any(), any(Runnable.class));
        }

        @Test
        @DisplayName("Should reject exp input exceeding available exp")
        void rejectInsufficientExp() throws Exception {
            addToWaitingForInput(uuid1, 1); // EXPERIENCE

            TradeSession session = new TradeSession(player1, player2);
            when(tradeService.getSession(uuid1)).thenReturn(session);
            when(tradeService.getTotalExperience(player1)).thenReturn(100);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player1).sendMessage(contains("\u7ECF\u9A8C\u4E0D\u8DB3")); // "经验不足"
        }

        @Test
        @DisplayName("Should handle trade ended during input")
        void tradeEndedDuringInput() throws Exception {
            addToWaitingForInput(uuid1, 0); // MONEY

            when(tradeService.getSession(uuid1)).thenReturn(null);

            AsyncPlayerChatEvent event = new AsyncPlayerChatEvent(false, player1, "500", new HashSet<>());

            listener.onPlayerChat(event);

            assertThat(event.isCancelled()).isTrue();
            verify(player1).sendMessage(contains("\u4EA4\u6613\u5DF2\u7ED3\u675F")); // "交易已结束"
        }

        /**
         * Helper to add a player to the waiting for input map.
         */
        private void addToWaitingForInput(UUID uuid, int typeOrdinal) throws Exception {
            Map<UUID, ?> waitingForInput = UltiTradeTestHelper.getField(listener, "waitingForInput");
            Class<?> inputTypeClass = Class.forName("com.ultikits.plugins.trade.listener.TradeListener$InputType");
            Object inputType = inputTypeClass.getEnumConstants()[typeOrdinal];
            @SuppressWarnings("unchecked")
            Map<UUID, Object> typedMap = (Map<UUID, Object>) waitingForInput;
            typedMap.put(uuid, inputType);
        }
    }

    /**
     * The trade window governs its own 54 slots. The acting player's own inventory is not part of
     * anybody's offer, and a click there was refused along with the rest — which left no production
     * gesture able to put an item on the cursor at all, while the place branch reads
     * {@code event.getCursor()}. Nothing could ever be staked: not the pane of
     * UltiKits/UltiTrade#31, not any item (UltiKits/UltiTrade#39).
     * <p>
     * Three actions stay refused even when the clicked slot is the player's own, because they are
     * not confined to the slot they were clicked on: a shift-click scans the trade window for
     * somewhere to put the stack, a double-click sweeps every slot of both inventories, and an
     * unknown action has unknown reach.
     * <p>
     * The harness performs exactly one half of the server's behaviour and no more: when the module
     * does not cancel a click on an own inventory slot, vanilla moves that slot's stack onto the
     * cursor. Everything else — what the session holds, what the inventory is handed, what reaches
     * the ground — is the module's own doing, observed rather than modelled.
     */
    @Nested
    @DisplayName("the trade window governs its own slots, not the player's own inventory (UltiKits/UltiTrade#39)")
    class OwnInventoryIsNotGoverned {

        /** The first raw slot a 54-slot window maps to the acting player's own inventory. */
        private static final int FIRST_OWN_SLOT = TradeGUI.SIZE;
        /** The last one: 36 mapped slots, the 27 storage slots plus the 9 hotbar slots. */
        private static final int LAST_OWN_SLOT = TradeGUI.SIZE + 35;
        /**
         * A trade-window slot that {@code TradeGUI#initializeGUI} leaves genuinely empty — it is in
         * neither slot array and no update method writes it. Used as a drag target because vanilla
         * only admits a slot to a drag it can actually place into, so dragging across two slots that
         * hold placeholder panes produces no drag event at all and would prove nothing.
         */
        private static final int EMPTY_WINDOW_SLOT = 39;

        private TradeGUI gui;
        private TradeSession session;

        @BeforeEach
        void openWindow() throws Exception {
            TradeService realService = new TradeService();
            // The module plugin, whose language file the service reads (UltiKits/UltiTrade#16)
            UltiTradeTestHelper.setField(realService, "plugin", UltiTradeTestHelper.getMockPlugin());
            UltiTradeTestHelper.setField(realService, "config", config);
            UltiTradeTestHelper.setField(realService, "logService", mock(TradeLogService.class));
            UltiTradeTestHelper.setField(listener, "tradeService", realService);

            session = new TradeSession(player1, player2);
            gui = mock(TradeGUI.class);
            when(gui.getSession()).thenReturn(session);
            when(gui.isYourSlot(TradeGUI.YOUR_SLOTS[0])).thenReturn(true);
            when(gui.getItemIndex(TradeGUI.YOUR_SLOTS[0])).thenReturn(0);
        }

        /**
         * @param cancelled a one-element sink recording whether the module refused this click, read
         *                  instead of {@code verify} so a test can branch on it
         */
        private InventoryClickEvent click(InventoryHolder holder, int rawSlot, InventoryAction action,
                                         boolean[] cancelled) {
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(holder);
            InventoryClickEvent event = mock(InventoryClickEvent.class);
            when(event.getInventory()).thenReturn(top);
            lenient().when(event.getWhoClicked()).thenReturn(player1);
            when(event.getRawSlot()).thenReturn(rawSlot);
            lenient().when(event.getAction()).thenReturn(action);
            doAnswer(invocation -> {
                cancelled[0] = invocation.getArgument(0);
                return null;
            }).when(event).setCancelled(anyBoolean());
            return event;
        }

        private InventoryDragEvent drag(InventoryHolder holder, Integer... rawSlots) {
            Inventory top = mock(Inventory.class);
            when(top.getHolder()).thenReturn(holder);
            InventoryDragEvent event = mock(InventoryDragEvent.class);
            when(event.getInventory()).thenReturn(top);
            when(event.getRawSlots()).thenReturn(new LinkedHashSet<>(Arrays.asList(rawSlots)));
            return event;
        }

        /** How many panes the trade session is holding as this player's own offer. */
        private int stakedPanes() {
            int total = 0;
            for (ItemStack staked : session.getPlayerItems(uuid1).values()) {
                if (staked != null && staked.getType() == Material.LIME_STAINED_GLASS_PANE) {
                    total += staked.getAmount();
                }
            }
            return total;
        }

        /**
         * A click on the acting player's own first offer slot, carrying whatever the ledger says is
         * currently on the cursor.
         */
        private InventoryClickEvent offerSlotClick(InventoryView view, int[] onCursor) {
            boolean[] cancelled = {false};
            InventoryClickEvent event =
                    click(gui, TradeGUI.YOUR_SLOTS[0], InventoryAction.PLACE_ALL, cancelled);
            when(event.getCursor()).thenAnswer(invocation -> onCursor[0] > 0
                    ? new ItemStack(Material.LIME_STAINED_GLASS_PANE, onCursor[0])
                    : null);
            when(event.getView()).thenReturn(view);
            return event;
        }

        @Test
        @DisplayName("seven panes reach a trade slot through the player's own inventory and come back, none created or lost")
        void stakingThroughTheOwnInventoryConservesTheItem() {
            // One ledger over every place a pane can be. Every assertion below reads the ledger and
            // the session; none reads a return value.
            int[] inInventory = {7};
            int[] onCursor = {0};
            int[] onGround = {0};

            // Resolved before any stubbing begins: reaching through player1 inside a doAnswer(...)
            // chain is itself a mock call made while Mockito is mid-stub, which it rejects as
            // UnfinishedStubbing rather than as the assertion this test is about.
            Inventory ownInventory = player1.getInventory();
            World world = player1.getWorld();

            InventoryView view = mock(InventoryView.class);
            doAnswer(invocation -> {
                onCursor[0] = 0;
                return null;
            }).when(view).setCursor(isNull());

            when(ownInventory.addItem(any(ItemStack.class))).thenAnswer(invocation -> {
                inInventory[0] += invocation.getArgument(0, ItemStack.class).getAmount();
                return new HashMap<Integer, ItemStack>();
            });
            doAnswer(invocation -> {
                onGround[0] += invocation.getArgument(1, ItemStack.class).getAmount();
                return null;
            }).when(world).dropItemNaturally(any(Location.class), any(ItemStack.class));

            assertThat(inInventory[0] + onCursor[0] + onGround[0] + stakedPanes())
                    .as("before: seven panes, all of them in the player's own inventory")
                    .isEqualTo(7);

            // Gesture 1 — a plain left-click on the own inventory slot holding the panes.
            boolean[] refused = {false};
            listener.onInventoryClick(click(gui, FIRST_OWN_SLOT, InventoryAction.PICKUP_ALL, refused));
            if (!refused[0]) {
                // The one half a server performs, and therefore the one half this harness performs.
                onCursor[0] = inInventory[0];
                inInventory[0] = 0;
            }

            // Gesture 2 — click the acting player's own first offer slot while holding them.
            listener.onInventoryClick(offerSlotClick(view, onCursor));

            assertThat(stakedPanes())
                    .as("the panes must reach the trade window; before UltiKits/UltiTrade#39 no gesture could put them there")
                    .isEqualTo(7);
            assertThat(inInventory[0] + onCursor[0] + onGround[0] + stakedPanes())
                    .as("staking creates nothing and loses nothing")
                    .isEqualTo(7);

            // Gesture 3 — the same slot again with an empty cursor: the take-back.
            listener.onInventoryClick(offerSlotClick(view, onCursor));

            assertThat(stakedPanes()).as("the offer left the trade window").isZero();
            assertThat(inInventory[0])
                    .as("all seven panes are back in the player's own inventory")
                    .isEqualTo(7);
            assertThat(onGround[0]).as("the inventory had room, so nothing was dropped").isZero();
            assertThat(inInventory[0] + onCursor[0] + onGround[0] + stakedPanes())
                    .as("after the whole round trip: still exactly seven panes, none created, none lost")
                    .isEqualTo(7);
        }

        @Test
        @DisplayName("a plain pickup in the player's own inventory is not refused, so an item can reach the cursor")
        void plainPickupInTheOwnInventoryIsNotRefused() {
            boolean[] cancelled = {false};

            listener.onInventoryClick(click(gui, FIRST_OWN_SLOT, InventoryAction.PICKUP_ALL, cancelled));

            assertThat(cancelled[0])
                    .as("a pickup in the player's own inventory is the only way to load the cursor")
                    .isFalse();
        }

        @Test
        @DisplayName("positive control: in the same fixture a click on a trade-window slot IS refused, so the handler ran")
        void aTradeWindowClickIsStillRefused() {
            boolean[] cancelled = {false};

            listener.onInventoryClick(
                    click(gui, TradeGUI.SEPARATOR_SLOTS[0], InventoryAction.PICKUP_ALL, cancelled));

            assertThat(cancelled[0])
                    .as("a separator pane is part of the window and can never be taken")
                    .isTrue();
        }

        @Test
        @DisplayName("the player's region ends where the view's mapping ends")
        void theOwnInventoryRegionEndsWhereTheViewDoes() {
            boolean[] lastOwn = {false};
            listener.onInventoryClick(click(gui, LAST_OWN_SLOT, InventoryAction.PICKUP_ALL, lastOwn));
            assertThat(lastOwn[0]).as("the hotbar's last slot belongs to the player").isFalse();

            boolean[] pastTheEnd = {false};
            listener.onInventoryClick(click(gui, LAST_OWN_SLOT + 1, InventoryAction.PICKUP_ALL, pastTheEnd));
            assertThat(pastTheEnd[0])
                    .as("a raw slot this view maps to neither inventory is refused")
                    .isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = InventoryAction.class,
                names = {"MOVE_TO_OTHER_INVENTORY", "COLLECT_TO_CURSOR", "UNKNOWN"})
        @DisplayName("an action reaching past the clicked slot is refused even in the player's own inventory")
        void reachingActionsAreRefusedFromTheOwnInventory(InventoryAction action) {
            boolean[] cancelled = {false};

            listener.onInventoryClick(click(gui, FIRST_OWN_SLOT, action, cancelled));

            assertThat(cancelled[0])
                    .as("%s can move an item into the trade window from an own inventory slot", action)
                    .isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = InventoryAction.class,
                names = {"PICKUP_ALL", "PICKUP_HALF", "PICKUP_ONE", "PLACE_ALL", "PLACE_ONE",
                         "SWAP_WITH_CURSOR", "HOTBAR_SWAP", "DROP_ALL_SLOT", "DROP_ONE_SLOT"})
        @DisplayName("an action confined to the clicked slot is left to the server in the player's own inventory")
        void confinedActionsAreAllowedInTheOwnInventory(InventoryAction action) {
            boolean[] cancelled = {false};

            listener.onInventoryClick(click(gui, FIRST_OWN_SLOT, action, cancelled));

            assertThat(cancelled[0])
                    .as("%s cannot reach the trade window from an own inventory slot", action)
                    .isFalse();
        }

        @Test
        @DisplayName("a click presented without an action is refused rather than guessed at")
        void anActionlessClickIsRefused() {
            boolean[] cancelled = {false};

            listener.onInventoryClick(click(gui, FIRST_OWN_SLOT, null, cancelled));

            assertThat(cancelled[0])
                    .as("a real event always carries an action; a caller that omits one gets the safe half")
                    .isTrue();
        }

        @Test
        @DisplayName("a drag confined to the player's own inventory is not refused")
        void aDragConfinedToTheOwnInventoryIsNotRefused() {
            InventoryDragEvent event = drag(gui, FIRST_OWN_SLOT, FIRST_OWN_SLOT + 1);

            listener.onInventoryDrag(event);

            verify(event, never()).setCancelled(true);
        }

        @Test
        @DisplayName("a drag touching even one trade-window slot is refused entirely")
        void aDragTouchingTheTradeWindowIsRefused() {
            InventoryDragEvent event = drag(gui, FIRST_OWN_SLOT, EMPTY_WINDOW_SLOT);

            listener.onInventoryDrag(event);

            verify(event).setCancelled(true);
        }

        @Test
        @DisplayName("the confirm page governs its own slots on the same terms")
        void theConfirmPageGovernsItsOwnSlotsOnly() {
            TradeConfirmPage page = mock(TradeConfirmPage.class);

            boolean[] ownSlot = {false};
            listener.onInventoryClick(click(page, TradeConfirmPage.SIZE, InventoryAction.PICKUP_ALL, ownSlot));
            assertThat(ownSlot[0])
                    .as("the viewer's own inventory is not part of a preview of somebody's offer")
                    .isFalse();
            verify(page, never()).handleClick(any(InventoryClickEvent.class));

            boolean[] pageSlot = {false};
            InventoryClickEvent pageClick =
                    click(page, TradeConfirmPage.CONFIRM_SLOT, InventoryAction.PICKUP_ALL, pageSlot);
            listener.onInventoryClick(pageClick);
            assertThat(pageSlot[0])
                    .as("positive control: a click inside the preview is still refused")
                    .isTrue();
            verify(page).handleClick(pageClick);
        }

        @Test
        @DisplayName("a drag confined to the player's own inventory is not refused while the confirm page is open either")
        void aConfirmPageDragInTheOwnInventoryIsNotRefused() {
            TradeConfirmPage page = mock(TradeConfirmPage.class);
            InventoryDragEvent event = drag(page, TradeConfirmPage.SIZE, TradeConfirmPage.SIZE + 1);

            listener.onInventoryDrag(event);

            verify(event, never()).setCancelled(true);
        }
    }

}
