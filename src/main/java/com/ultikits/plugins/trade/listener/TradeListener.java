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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Track players waiting for input (money/exp). Read and removed on the chat thread as well as the
    // server thread, so it has to be a concurrent map: the timeout's remove(key, value) is atomic only there.
    private final Map<UUID, PendingPrompt> waitingForInput = new ConcurrentHashMap<>();

    // A player's prompt is claimed (removed from waitingForInput) the instant their chat answer
    // arrives, but the answer is only applied on the server thread afterward. This records which
    // trade that claimed-but-not-yet-applied answer belongs to, so the close guard's
    // isAnsweringPromptOf still sees the player as answering in that window (UltiKits/UltiTrade#47
    // review). Written on the chat thread (possibly async) and read/cleared on the server thread.
    private final Map<UUID, TradeSession> answeringSessions = new ConcurrentHashMap<>();

    enum InputType {
        MONEY, EXPERIENCE
    }

    /** A chat prompt a player has open: what it asks for, and the trade it was opened in. */
    static final class PendingPrompt {
        final InputType type;
        final TradeSession session;

        PendingPrompt(InputType type, TradeSession session) {
            this.type = type;
            this.session = session;
        }
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
            // Opening the prompt changes no offer, so no confirmation is touched here: setting an
            // amount resets both through TradeSession#setMoney, and reopenGUI repaints both windows
            // (UltiKits/UltiTrade#36).

            // Start money input conversation
            player.closeInventory();
            player.sendMessage(text(tradeService.i18n("input_money_prompt")));
            player.sendMessage(text(tradeService.i18n("input_cancel")));
            PendingPrompt prompt = new PendingPrompt(InputType.MONEY, session);
            waitingForInput.put(player.getUniqueId(), prompt);

            // Reopen GUI after a delay if no input
            scheduleReopenAfterTimeout(player, prompt);
            return;
        }
        
        // Handle experience slot click
        if (gui.isExpSlot(slot) && config.isEnableExpTrade()) {
            // As for money: TradeSession#setExp resets both confirmations when an amount is set
            // (UltiKits/UltiTrade#36).

            // Start exp input conversation
            player.closeInventory();
            player.sendMessage(text(tradeService.i18n("input_exp_prompt")));
            player.sendMessage(text(tradeService.i18n("input_exp_current")
                .replace("{AMOUNT}", String.valueOf(tradeService.getTotalExperience(player)))));
            player.sendMessage(text(tradeService.i18n("input_cancel")));
            PendingPrompt prompt = new PendingPrompt(InputType.EXPERIENCE, session);
            waitingForInput.put(player.getUniqueId(), prompt);

            // Reopen GUI after a delay if no input
            scheduleReopenAfterTimeout(player, prompt);
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
            // Confirmations are reset only where the offer really changes -- TradeSession#setItem
            // resets both -- and each of those paths repaints both windows. Resetting here, before
            // anything was decided, cleared both players' confirmations on a click that changed
            // nothing and left both windows showing them as confirmed (UltiKits/UltiTrade#36).
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
     * Whether {@code playerUuid} has a chat prompt open that was opened in {@code session}, OR has
     * already answered one and that answer is still on its way to the server thread.
     * <p>
     * {@code onPlayerChat} claims a prompt (removes it from {@link #waitingForInput}) the instant the
     * chat message arrives, on whichever thread that is, so a second message cannot also claim it; the
     * answer itself is only applied afterward, on the server thread, by {@link #answerPrompt}. Between
     * those two points the prompt is gone from {@code waitingForInput}, but the trade is not idle -- the
     * player has already answered, only the write has not landed. A close-guard check that ran only
     * against {@code waitingForInput} in that window would see nobody answering and could cancel the
     * trade the tick after a valid answer arrived, deferred only by ordinary scheduler timing rather
     * than by anything the player did (UltiKits/UltiTrade#47 review). {@link #answeringSessions} is
     * cleared as soon as {@link #answerPrompt} finishes, success or not, so this window is no wider than
     * it has to be.
     */
    private boolean isAnsweringPromptOf(UUID playerUuid, TradeSession session) {
        PendingPrompt prompt = waitingForInput.get(playerUuid);
        if (prompt != null && session != null && prompt.session == session) {
            return true;
        }
        TradeSession claimed = answeringSessions.get(playerUuid);
        return claimed != null && claimed == session;
    }

    /**
     * Ends {@code prompt} after 10 seconds without an answer and reopens its trade's window.
     * <p>
     * The task acts only on its own prompt and its own trade. It used to check only whether the
     * player was trading at all -- true again once they had opened a newer trade -- so a prompt left
     * over from a cancelled trade reopened the old window over the new one, whose close then
     * cancelled the new trade; and it removed whatever prompt the player had open, including one
     * opened in the newer trade (UltiKits/UltiTrade#40).
     */
    private void scheduleReopenAfterTimeout(Player player, PendingPrompt prompt) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(getBukkitPlugin(), () -> {
            if (waitingForInput.remove(uuid, prompt) && tradeService.getSession(uuid) == prompt.session) {
                TradeGUI newGui = new TradeGUI(tradeService, prompt.session, player);
                openOrCancel(player, prompt.session, newGui.getInventory());
            }
        }, 200L); // 10 seconds timeout
    }

    /**
     * Opens {@code inventory} for {@code player}, and cancels {@code session} instead of leaving the
     * player with no trade UI at all if another plugin refuses the {@code InventoryOpenEvent} this
     * fires. {@code HumanEntity#openInventory} is {@code @Nullable}: a refused open returns {@code
     * null} rather than throwing, which is easy to miss because nothing crashes -- the trade would
     * otherwise keep running with both stakes locked, recoverable only by someone noticing and
     * cancelling it by hand (UltiKits/UltiTrade#47 review; {@link TradeService#openOrCancel} is the
     * same check for that class's own callers).
     *
     * @return true if the inventory actually opened; false if another plugin refused it (in which case
     *         {@code session} has already been cancelled)
     */
    private boolean openOrCancel(Player player, TradeSession session, Inventory inventory) {
        if (player.openInventory(inventory) != null) {
            return true;
        }
        tradeService.cancelTrade(session, tradeService.i18n("cancel_reason_window_refused"));
        return false;
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
     * <p>
     * The chat event normally arrives on the chat thread. There the answer is only claimed (the
     * prompt removed, the message hidden); it is applied on the server thread, where every other
     * change to a trade happens. Applied from the chat thread, an amount could land between a
     * confirmation page's check that the offer is unchanged and its confirmation, leaving a player
     * confirmed against an amount their page never showed.
     * <p>
     * Claiming also marks the trade as answering in {@link #answeringSessions}, kept until
     * {@link #answerPrompt} finishes, so a close-guard check that runs in the gap between the claim and
     * the applied answer still sees the player as answering rather than concluding nobody is
     * (UltiKits/UltiTrade#47 review).
     */
    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PendingPrompt prompt = waitingForInput.remove(uuid);

        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        // A session-less prompt (a test fixture only; production always opens a prompt in a real
        // session) has nothing for the close guard to protect, and ConcurrentHashMap rejects a null
        // value outright.
        if (prompt.session != null) {
            answeringSessions.put(uuid, prompt.session);
        }
        String message = event.getMessage().trim();
        if (event.isAsynchronous()) {
            Bukkit.getScheduler().runTask(getBukkitPlugin(), () -> applyAnswer(player, prompt, message));
        } else {
            applyAnswer(player, prompt, message);
        }
    }

    /** Runs {@link #answerPrompt}, then always clears this answer's {@link #answeringSessions} entry. */
    private void applyAnswer(Player player, PendingPrompt prompt, String message) {
        try {
            answerPrompt(player, prompt, message);
        } finally {
            if (prompt.session != null) {
                answeringSessions.remove(player.getUniqueId(), prompt.session);
            }
        }
    }

    /** Applies {@code message} as the answer to {@code prompt}. Runs on the server thread. */
    private void answerPrompt(Player player, PendingPrompt prompt, String message) {
        InputType inputType = prompt.type;

        // Check for cancel
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(text(tradeService.i18n("input_cancelled")));
            Bukkit.getScheduler().runTask(getBukkitPlugin(), () -> {
                TradeSession session = tradeService.getSession(player.getUniqueId());
                // Only the prompt's own trade is reopened (UltiKits/UltiTrade#40).
                if (session != null && session == prompt.session) {
                    TradeGUI gui = new TradeGUI(tradeService, session, player);
                    // A refused open cancels the trade instead of leaving the player with no window
                    // at all (UltiKits/UltiTrade#47 review).
                    openOrCancel(player, session, gui.getInventory());
                }
            });
            return;
        }
        
        // Parse number
        try {
            double value = Double.parseDouble(message);
            // NaN and the infinities parse, and NaN passes both the negative and the balance check,
            // after which every "money > 0" test at completion is false: the money transfer was
            // skipped while the items still moved (UltiKits/UltiTrade#29).
            if (!Double.isFinite(value)) {
                player.sendMessage(text(tradeService.i18n("invalid_amount")));
                reopenGUI(player, prompt);
                return;
            }
            if (value < 0) {
                player.sendMessage(text(tradeService.i18n("amount_negative")));
                reopenGUI(player, prompt);
                return;
            }
            
            // The amount belongs to the trade the prompt was opened in; if that trade has ended, even
            // when a newer one has started since, it is not applied (UltiKits/UltiTrade#40).
            TradeSession session = tradeService.getSession(player.getUniqueId());
            if (session == null || session != prompt.session) {
                player.sendMessage(text(tradeService.i18n("trade_ended")));
                return;
            }
            
            if (inputType == InputType.MONEY) {
                // Read the provider once (UltiKits/UltiTrade#26). Without a provider the amount stays
                // unchanged. getEconomy() returns Object, not Economy -- see TradeService's field
                // comment; casting here is safe because a non-null value only ever holds an Economy.
                Economy currentEconomy = (Economy) tradeService.getEconomy();
                if (currentEconomy == null || !config.isEnableMoneyTrade()) {
                    // 0 withdraws an offer made while money trading was available; nothing is taken
                    // from anybody (UltiKits/UltiTrade#28).
                    if (value == 0) {
                        session.setMoney(player.getUniqueId(), 0);
                        player.sendMessage(text(tradeService.i18n("money_set").replace("{AMOUNT}", String.valueOf(0.0))));
                        reopenGUI(player, prompt);
                        return;
                    }
                    player.sendMessage(text(tradeService.i18n("money_unavailable")));
                    reopenGUI(player, prompt);
                    return;
                }
                // Check balance
                if (currentEconomy.getBalance(player) < value) {
                    player.sendMessage(text(tradeService.i18n("insufficient_money")));
                    reopenGUI(player, prompt);
                    return;
                }
                session.setMoney(player.getUniqueId(), value);
                player.sendMessage(text(tradeService.i18n("money_set").replace("{AMOUNT}", String.valueOf(value))));
            } else {
                // A reload may have turned experience trading off since the prompt opened
                // (UltiKits/UltiTrade#26). Without it the amount stays unchanged.
                if (!config.isEnableExpTrade()) {
                    // As for money: 0 withdraws an offer (UltiKits/UltiTrade#28).
                    if ((int) value == 0) {
                        session.setExp(player.getUniqueId(), 0);
                        player.sendMessage(text(tradeService.i18n("exp_set").replace("{AMOUNT}", "0")));
                        reopenGUI(player, prompt);
                        return;
                    }
                    player.sendMessage(text(tradeService.i18n("exp_unavailable")));
                    reopenGUI(player, prompt);
                    return;
                }
                // Check experience
                int expValue = (int) value;
                if (tradeService.getTotalExperience(player) < expValue) {
                    player.sendMessage(text(tradeService.i18n("insufficient_exp")));
                    reopenGUI(player, prompt);
                    return;
                }
                session.setExp(player.getUniqueId(), expValue);
                player.sendMessage(text(tradeService.i18n("exp_set").replace("{AMOUNT}", String.valueOf(expValue))));
            }
            
            reopenGUI(player, prompt);
            
        } catch (NumberFormatException e) {
            player.sendMessage(text(tradeService.i18n("invalid_amount")));
            reopenGUI(player, prompt);
        }
    }
    
    /**
     * Reopens the trade window of the trade {@code prompt} was opened in, if that is still the
     * player's trade. A prompt left over from an ended trade must not reopen anything over a newer
     * trade, whose window being replaced would cancel it (UltiKits/UltiTrade#40).
     */
    private void reopenGUI(Player player, PendingPrompt prompt) {
        Bukkit.getScheduler().runTask(getBukkitPlugin(), () -> {
            TradeSession session = tradeService.getSession(player.getUniqueId());
            if (session != null && session == prompt.session) {
                TradeGUI gui = new TradeGUI(tradeService, session, player);
                // A refused open cancels the trade instead of leaving the player with no window at
                // all; updating both windows for an already-cancelled trade would be meaningless
                // (UltiKits/UltiTrade#47 review).
                if (openOrCancel(player, session, gui.getInventory())) {
                    updateBothGUIs(session);
                }
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
            // Closing the confirmation page never cancels the trade by itself. Only the player's own
            // close (Esc, reason PLAYER) counts as the page's Cancel and goes back to the trade window.
            // A close by a plugin -- this module cancelling or completing the trade, a reload redrawing
            // it -- is not the player's answer, and running Cancel there would schedule a trade window
            // nobody asked for (during shutdown, on a scheduler that refuses tasks).
            //
            // Every other close is terminal, cancelling the trade the same deferred, session-checked
            // way DEATH already did before this sweep (UltiKits/UltiTrade#47 review, sweeping every
            // InventoryCloseEvent.Reason): the server deciding, independently of this module, that the
            // player may no longer see the page (they died, disconnected, teleported, their chunk
            // unloaded, or Paper otherwise revoked access), an unrecognised reason, OR -- the narrowing
            // this review round added -- a PLUGIN/OPEN_NEW close this module did NOT cause. Paper
            // reports the identical PLUGIN or OPEN_NEW reason whether this module's own
            // closeInventory()/openInventory() call triggered the close or an unrelated plugin did
            // (closing the page itself, or opening its own window over it), so the reason alone cannot
            // tell those two apart; {@link TradeConfirmPage#isAnswered()} can, because every one of this
            // module's own closes marks the page first (a button click, this branch's own dismiss()
            // below, or TradeService's cancelTrade/completeTrade/refreshOpenTradeWindow dismissing it
            // before closing or replacing it). A PLUGIN/OPEN_NEW close this module did not already mark
            // left the trade running with both stakes locked and no window, exactly like the reasons
            // this sweep already covered, until someone ran the cancel command by hand. TELEPORT is
            // deprecated since Paper 1.21.10 ("not called anymore as inventories are not closed on
            // teleportation") and so cannot fire on this module's target server, but the constant is not
            // removed and this module's own `plugin.yml` declares `api-version: '1.19'` for
            // compatibility with older servers that may still send it, so it stays in the terminal set
            // rather than being dropped as dead code.
            TradeConfirmPage page = (TradeConfirmPage) event.getInventory().getHolder();
            if (!page.isViewer(event.getPlayer())) {
                // Somebody else closing their view of this page; the viewer still has it open.
                return;
            }
            InventoryCloseEvent.Reason reason = event.getReason();
            if (reason == InventoryCloseEvent.Reason.PLAYER) {
                page.handleClose();
                return;
            }
            boolean alreadyAccountedFor = page.isAnswered();
            page.dismiss();
            if (!alreadyAccountedFor) {
                Player affectedPlayer = (Player) event.getPlayer();
                TradeSession affectedSession = tradeService.getSession(affectedPlayer.getUniqueId());
                if (affectedSession != null) {
                    Bukkit.getScheduler().runTaskLater(
                        getBukkitPlugin(),
                        () -> {
                            if (tradeService.getSession(affectedPlayer.getUniqueId()) == affectedSession) {
                                tradeService.cancelTrade(affectedPlayer);
                            }
                        },
                        1L
                    );
                }
            }
            return;
        }
        
        if (!(event.getInventory().getHolder() instanceof TradeGUI)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        TradeSession session = tradeService.getSession(player.getUniqueId());

        // Don't cancel while the player answers a prompt of this trade. Only of this trade: a prompt
        // left over from a trade cancelled while it was open stays until its timeout, and must not keep
        // a newer trade running with no window (UltiKits/UltiTrade#40).
        if (isAnsweringPromptOf(player.getUniqueId(), session)) {
            return;
        }

        // Nor when the window was closed to open the large-trade confirmation page
        // (UltiKits/UltiTrade#23).
        if (tradeService.isOpeningConfirmPage(player.getUniqueId())) {
            return;
        }
        
        if (session != null && session.getState() == TradeSession.TradeState.TRADING) {
            // Cancel trade when closing GUI -- this trade, the one whose window closed, and only if it
            // is still the player's trade when the task runs (UltiKits/UltiTrade#40).
            Bukkit.getScheduler().runTaskLater(
                getBukkitPlugin(),
                () -> {
                    if (tradeService.getSession(player.getUniqueId()) == session
                            && !isAnsweringPromptOf(player.getUniqueId(), session)) {
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
