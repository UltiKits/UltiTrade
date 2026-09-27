package com.ultikits.plugins.trade.listener;

import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.gui.TradeGUI;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for trade GUI interactions and shift+right-click trading.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@EventListener
public class TradeListener implements Listener {
    
    @Autowired
    private TradeService tradeService;
    
    @Autowired
    private TradeConfig config;
    
    /**
     * How many of the acting player's own inventory slots a container view maps into its raw-slot
     * range: the 27 storage slots plus the 9 hotbar slots, immediately after the window's own slots.
     * The armour and off-hand slots are not mapped into a chest-style view at all, so a raw slot past
     * this range belongs to neither inventory and is refused rather than assumed to be the player's.
     */
    private static final int MAPPED_PLAYER_SLOTS = 36;

    // Track players waiting for input (money/exp)
    private final Map<UUID, InputType> waitingForInput = new HashMap<>();
    
    private enum InputType {
        MONEY, EXPERIENCE
    }

    /**
     * Get Bukkit plugin instance for scheduler tasks.
     * Uses lazy lookup to avoid initialization ordering issues.
     */
    private Plugin getBukkitPlugin() {
        return Bukkit.getPluginManager().getPlugin("UltiTools");
    }
    
    /**
     * Handle shift+right-click on players to request trade.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!config.isEnableShiftClick()) {
            return;
        }
        
        // Only handle main hand clicks
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();
        
        // Check if shift+right-click on a player
        if (!player.isSneaking() || !(entity instanceof Player)) {
            return;
        }
        
        Player target = (Player) entity;
        
        // Don't allow trading with self
        if (player.equals(target)) {
            return;
        }
        
        // Check permission
        if (!player.hasPermission("ultitrade.use")) {
            return;
        }
        
        event.setCancelled(true);
        tradeService.sendRequest(player, target);
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Handle TradeConfirmPage clicks
        if (event.getInventory().getHolder() instanceof TradeConfirmPage) {
            if (isConfinedToOwnInventory(event, TradeConfirmPage.SIZE)) {
                return;
            }
            event.setCancelled(true);
            TradeConfirmPage confirmPage = (TradeConfirmPage) event.getInventory().getHolder();
            confirmPage.handleClick(event);
            return;
        }
        
        // Handle TradeGUI clicks
        if (!(event.getInventory().getHolder() instanceof TradeGUI)) {
            return;
        }
        
        TradeGUI gui = (TradeGUI) event.getInventory().getHolder();
        Player player = (Player) event.getWhoClicked();
        TradeSession session = gui.getSession();
        int slot = event.getRawSlot();

        // Only the two traders may act on this window. TradeGUI's slot test carries no perspective and
        // TradeSession treats everyone who is not player 1 as player 2, so a third viewer of this
        // inventory — which another plugin can arrange — would otherwise have their click handled as
        // player 2's, and since UltiKits/UltiTrade#31 an occupied-slot click always delivers the stored
        // offer to whoever clicked (UltiKits/UltiTrade#38).
        //
        // Deliberately ahead of the region test below, so a non-participant is refused everywhere in
        // this view including their own inventory half: somebody who is not in the trade has no
        // business in this window at all, and they can only be looking at it because another plugin
        // put them there. The cost is a restriction in an already-abnormal state; the alternative
        // would widen an item-safety guard for no reachable benefit.
        if (!session.isParticipant(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // The acting player's own inventory is not part of anybody's offer, and refusing a click
        // there left no production gesture able to put an item on the cursor — which the place branch
        // below reads — so nothing could ever be staked (UltiKits/UltiTrade#39).
        if (!isTradeWindowSlot(slot)) {
            if (isConfinedToOwnInventory(event, TradeGUI.SIZE)) {
                return;
            }
            event.setCancelled(true);
            return;
        }

        // Every slot of the trade window holds a display or control item, and the player's own
        // offered items are managed through the session, so no click may ever move an item by
        // itself. Cancel first; whether a feature is enabled decides only which action runs below
        // (UltiKits/UltiTrade#25).
        event.setCancelled(true);

        // Handle confirm button
        if (slot == TradeGUI.CONFIRM_SLOT) {
            if (session.isConfirmed(player.getUniqueId())) {
                tradeService.cancelConfirmation(player);
            } else {
                tradeService.confirmTrade(player);
            }
            updateBothGUIs(session);
            return;
        }
        
        // Handle cancel button
        if (slot == TradeGUI.CANCEL_SLOT) {
            tradeService.cancelTrade(player);
            return;
        }
        
        // Handle money slot click
        if (gui.isMoneySlot(slot) && tradeService.hasEconomy()) {
            // Reset confirmation when changing money
            session.setConfirmed(player.getUniqueId(), false);
            session.setConfirmed(session.getOtherPlayer(player.getUniqueId()), false);
            
            // Start money input conversation
            player.closeInventory();
            player.sendMessage(text(tradeService.i18n("input_money_prompt")));
            player.sendMessage(text(tradeService.i18n("input_cancel")));
            waitingForInput.put(player.getUniqueId(), InputType.MONEY);
            
            // Reopen GUI after a delay if no input
            Bukkit.getScheduler().runTaskLater(getBukkitPlugin(), () -> {
                if (waitingForInput.remove(player.getUniqueId()) != null) {
                    if (tradeService.isTrading(player.getUniqueId())) {
                        TradeGUI newGui = new TradeGUI(tradeService, session, player);
                        player.openInventory(newGui.getInventory());
                    }
                }
            }, 200L); // 10 seconds timeout
            return;
        }
        
        // Handle experience slot click
        if (gui.isExpSlot(slot) && config.isEnableExpTrade()) {
            // Reset confirmation when changing exp
            session.setConfirmed(player.getUniqueId(), false);
            session.setConfirmed(session.getOtherPlayer(player.getUniqueId()), false);
            
            // Start exp input conversation
            player.closeInventory();
            player.sendMessage(text(tradeService.i18n("input_exp_prompt")));
            player.sendMessage(text(tradeService.i18n("input_exp_current")
                .replace("{AMOUNT}", String.valueOf(tradeService.getTotalExperience(player)))));
            player.sendMessage(text(tradeService.i18n("input_cancel")));
            waitingForInput.put(player.getUniqueId(), InputType.EXPERIENCE);
            
            // Reopen GUI after a delay if no input
            Bukkit.getScheduler().runTaskLater(getBukkitPlugin(), () -> {
                if (waitingForInput.remove(player.getUniqueId()) != null) {
                    if (tradeService.isTrading(player.getUniqueId())) {
                        TradeGUI newGui = new TradeGUI(tradeService, session, player);
                        player.openInventory(newGui.getInventory());
                    }
                }
            }, 200L); // 10 seconds timeout
            return;
        }
        
        // Block other player's side
        for (int s : TradeGUI.THEIR_SLOTS) {
            if (s == slot) {
                return;
            }
        }
        
        // Block separator slots
        for (int s : TradeGUI.SEPARATOR_SLOTS) {
            if (s == slot) {
                return;
            }
        }
        
        // Block status and other reserved slots
        if (slot == TradeGUI.YOUR_STATUS_SLOT || slot == TradeGUI.THEIR_STATUS_SLOT ||
            slot == TradeGUI.THEIR_MONEY_SLOT || slot == TradeGUI.THEIR_EXP_SLOT ||
            (slot >= 45 && slot < 54 && slot != TradeGUI.CONFIRM_SLOT && slot != TradeGUI.CANCEL_SLOT &&
             slot != TradeGUI.YOUR_MONEY_SLOT && slot != TradeGUI.YOUR_EXP_SLOT)) {
            return;
        }
        
        // Handle your item slots
        if (gui.isYourSlot(slot)) {
            // Reset confirmation when changing items
            session.setConfirmed(player.getUniqueId(), false);
            session.setConfirmed(session.getOtherPlayer(player.getUniqueId()), false);
            
            ItemStack cursor = event.getCursor();
            int index = gui.getItemIndex(slot);

            // Whether this slot holds an offer is read from the session, which is the only authority
            // on what this player has put up. It used to be inferred from the rendered item's
            // material name ("...STAINED_GLASS_PANE" meant empty), and a player's own stained glass
            // pane is indistinguishable from the empty-slot placeholder that way: placing another
            // item over it silently replaced, and so destroyed, the stored pane, and it could not be
            // taken back out at all (UltiKits/UltiTrade#31).
            ItemStack offered = session.getPlayerItems(player.getUniqueId()).get(index);
            // getCursor() is @NotNull in this Paper API; the null half only guards a caller that
            // presents a bare event, and isAir() is the live test for "holding nothing".
            boolean holdingItem = cursor != null && !cursor.getType().isAir();

            if (offered == null) {
                // Empty slot: accept the item on the cursor, if there is one.
                if (holdingItem) {
                    session.setItem(player.getUniqueId(), index, cursor.clone());
                    event.getView().setCursor(null);
                    gui.playItemSound();
                    updateBothGUIs(session);
                }
                return;
            }

            // Occupied slot: the stored offer always leaves the slot and always comes back to the
            // player, whether this click is a plain take-back or a swap for the item on the cursor.
            // Whatever the inventory cannot hold is dropped at the player's feet rather than
            // discarded, the same contract the cancel and complete paths use (UltiKits/UltiTrade#20).
            // One slot write, and the hand-back immediately after it, so nothing sits between the
            // removal and the delivery.
            session.setItem(player.getUniqueId(), index, holdingItem ? cursor.clone() : null);
            tradeService.giveOrDrop(player, offered);
            if (holdingItem) {
                event.getView().setCursor(null);
                gui.playItemSound();
            }
            tradeService.playSound(player, Sound.ENTITY_ITEM_PICKUP);
            updateBothGUIs(session);
            return;
        }
    }
    
    /**
     * Whether a raw slot belongs to the trade window itself rather than to the acting player's own
     * inventory.
     * <p>
     * A negative raw slot — {@code -999}, a click outside every inventory — is not a window slot, and
     * is not one of the player's either, so it is governed by the caller's refusal branch.
     *
     * @param rawSlot the clicked raw slot
     * @return true if the slot is one of the trade window's own
     */
    private static boolean isTradeWindowSlot(int rawSlot) {
        return rawSlot >= 0 && rawSlot < TradeGUI.SIZE;
    }

    /**
     * Whether this interaction begins and ends inside the acting player's own inventory, and so is
     * none of this module's business.
     * <p>
     * Two conditions, both necessary. The raw slot has to be one the view maps to the player's own
     * inventory — {@code windowSize} up to {@code windowSize + }{@value #MAPPED_PLAYER_SLOTS}{@code
     * - 1}. And the action has to be confined to the slot it was clicked on: three are not, and are
     * refused from an own slot exactly as they are refused from a window slot.
     * <ul>
     *   <li>{@code MOVE_TO_OTHER_INVENTORY} — a shift-click from the player's side scans the trade
     *       window for somewhere to put the stack, so the item lands in a window the module owns.</li>
     *   <li>{@code COLLECT_TO_CURSOR} — a double-click sweeps every slot of <em>both</em> inventories
     *       for matching items, so it can pull a display item out of the window.</li>
     *   <li>{@code UNKNOWN} — reach unknown, so assume the widest.</li>
     * </ul>
     * That is the same three the GUI library this ecosystem builds on refuses, for the same reason.
     *
     * @param event      the click being considered
     * @param windowSize how many slots the open window has, which is where the player's own
     *                   inventory starts in the view's raw-slot range
     * @return true if the click may be left to the server
     */
    private static boolean isConfinedToOwnInventory(InventoryClickEvent event, int windowSize) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < windowSize || rawSlot >= windowSize + MAPPED_PLAYER_SLOTS) {
            return false;
        }
        InventoryAction action = event.getAction();
        if (action == null) {
            // A real event always carries an action; a caller presenting a bare event gets the safe
            // half rather than a guess.
            return false;
        }
        switch (action) {
            case MOVE_TO_OTHER_INVENTORY:
            case COLLECT_TO_CURSOR:
            case UNKNOWN:
                return false;
            default:
                return true;
        }
    }

    /**
     * Handle chat input for money/exp.
     */
    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        InputType inputType = waitingForInput.remove(player.getUniqueId());
        
        if (inputType == null) {
            return;
        }
        
        event.setCancelled(true);
        String message = event.getMessage().trim();
        
        // Check for cancel
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(text(tradeService.i18n("input_cancelled")));
            Bukkit.getScheduler().runTask(getBukkitPlugin(), () -> {
                TradeSession session = tradeService.getSession(player.getUniqueId());
                if (session != null && tradeService.isTrading(player.getUniqueId())) {
                    TradeGUI gui = new TradeGUI(tradeService, session, player);
                    player.openInventory(gui.getInventory());
                }
            });
            return;
        }
        
        // Parse number
        try {
            double value = Double.parseDouble(message);
            if (value < 0) {
                player.sendMessage(text(tradeService.i18n("amount_negative")));
                reopenGUI(player);
                return;
            }
            
            TradeSession session = tradeService.getSession(player.getUniqueId());
            if (session == null) {
                player.sendMessage(text(tradeService.i18n("trade_ended")));
                return;
            }
            
            if (inputType == InputType.MONEY) {
                // Read the provider once: a reload on the main thread may drop it while this async
                // handler runs (UltiKits/UltiTrade#26). Without a provider the amount stays unchanged.
                Economy currentEconomy = tradeService.getEconomy();
                if (currentEconomy == null || !config.isEnableMoneyTrade()) {
                    player.sendMessage(text(tradeService.i18n("money_unavailable")));
                    reopenGUI(player);
                    return;
                }
                // Check balance
                if (currentEconomy.getBalance(player) < value) {
                    player.sendMessage(text(tradeService.i18n("insufficient_money")));
                    reopenGUI(player);
                    return;
                }
                session.setMoney(player.getUniqueId(), value);
                player.sendMessage(text(tradeService.i18n("money_set").replace("{AMOUNT}", String.valueOf(value))));
            } else {
                // A reload may have turned experience trading off since the prompt opened
                // (UltiKits/UltiTrade#26). Without it the amount stays unchanged.
                if (!config.isEnableExpTrade()) {
                    player.sendMessage(text(tradeService.i18n("exp_unavailable")));
                    reopenGUI(player);
                    return;
                }
                // Check experience
                int expValue = (int) value;
                if (tradeService.getTotalExperience(player) < expValue) {
                    player.sendMessage(text(tradeService.i18n("insufficient_exp")));
                    reopenGUI(player);
                    return;
                }
                session.setExp(player.getUniqueId(), expValue);
                player.sendMessage(text(tradeService.i18n("exp_set").replace("{AMOUNT}", String.valueOf(expValue))));
            }
            
            reopenGUI(player);
            
        } catch (NumberFormatException e) {
            player.sendMessage(text(tradeService.i18n("invalid_amount")));
            reopenGUI(player);
        }
    }
    
    /**
     * Reopen trade GUI for player.
     */
    private void reopenGUI(Player player) {
        Bukkit.getScheduler().runTask(getBukkitPlugin(), () -> {
            TradeSession session = tradeService.getSession(player.getUniqueId());
            if (session != null && tradeService.isTrading(player.getUniqueId())) {
                TradeGUI gui = new TradeGUI(tradeService, session, player);
                player.openInventory(gui.getInventory());
                updateBothGUIs(session);
            }
        });
    }
    
    /**
     * Refuses a drag that touches the open window, and leaves alone one that does not.
     * <p>
     * {@code InventoryEvent#getInventory()} returns the <em>top</em> inventory of the view, so a drag
     * confined to the player's own inventory reports this module's holder and was refused with the
     * rest — the same defect as the click path's, one layer over (UltiKits/UltiTrade#39).
     * <p>
     * A drag is answered as a whole: {@code getRawSlots()} names every slot it would write, and there
     * is no way to apply it to some of them, so one window slot among them refuses all of it. The
     * refusal only ever adds a cancellation and never clears one, so a drag another plugin has
     * already refused stays refused.
     *
     * @param event the drag being considered
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        final int windowSize;
        if (event.getInventory().getHolder() instanceof TradeGUI) {
            windowSize = TradeGUI.SIZE;
        } else if (event.getInventory().getHolder() instanceof TradeConfirmPage) {
            windowSize = TradeConfirmPage.SIZE;
        } else {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < windowSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TradeConfirmPage) {
            // Don't cancel trade when closing confirm page
            return;
        }
        
        if (!(event.getInventory().getHolder() instanceof TradeGUI)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        // Don't cancel if waiting for input
        if (waitingForInput.containsKey(player.getUniqueId())) {
            return;
        }
        
        TradeSession session = tradeService.getSession(player.getUniqueId());
        
        if (session != null && session.getState() == TradeSession.TradeState.TRADING) {
            // Cancel trade when closing GUI
            Bukkit.getScheduler().runTaskLater(
                getBukkitPlugin(),
                () -> {
                    if (tradeService.isTrading(player.getUniqueId()) && 
                        !waitingForInput.containsKey(player.getUniqueId())) {
                        tradeService.cancelTrade(player);
                    }
                },
                1L
            );
        }
    }
    
    /**
     * Hand a joining player the stake of any trade that was cancelled while the server could not
     * find them (UltiKits/UltiTrade#32). Runs on the main thread, where the join event is fired.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        tradeService.deliverPendingReturns(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        waitingForInput.remove(player.getUniqueId());
        if (tradeService.isTrading(player.getUniqueId())) {
            tradeService.cancelTrade(player);
        }
    }
    
    /**
     * Update both players' GUIs.
     */
    private void updateBothGUIs(TradeSession session) {
        Player player1 = Bukkit.getPlayer(session.getPlayer1());
        Player player2 = Bukkit.getPlayer(session.getPlayer2());
        
        if (player1 != null && player1.getOpenInventory().getTopInventory().getHolder() instanceof TradeGUI) {
            ((TradeGUI) player1.getOpenInventory().getTopInventory().getHolder()).update();
        }
        if (player2 != null && player2.getOpenInventory().getTopInventory().getHolder() instanceof TradeGUI) {
            ((TradeGUI) player2.getOpenInventory().getTopInventory().getHolder()).update();
        }
    }

    /** The language file's text, {@code &} colour codes applied. */
    private static String text(String languageText) {
        return ChatColor.translateAlternateColorCodes('&', languageText);
    }
}
