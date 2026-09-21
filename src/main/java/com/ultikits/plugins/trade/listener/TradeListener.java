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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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

        // Every slot of the trade window holds a display or control item, and the player's own
        // offered items are managed through the session, so no click may ever move an item by
        // itself. Cancel first; whether a feature is enabled decides only which action runs below
        // (UltiKits/UltiTrade#25).
        event.setCancelled(true);
        
        // Click outside the trade GUI
        if (slot >= 54) {
            return;
        }
        
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
            player.sendMessage(ChatColor.GOLD + "请在聊天框中输入要交易的金币数量：");
            player.sendMessage(ChatColor.GRAY + "(输入 'cancel' 取消)");
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
            player.sendMessage(ChatColor.GREEN + "请在聊天框中输入要交易的经验值：");
            player.sendMessage(ChatColor.AQUA + "你当前有 " + tradeService.getTotalExperience(player) + " 经验");
            player.sendMessage(ChatColor.GRAY + "(输入 'cancel' 取消)");
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
            session.setItem(player.getUniqueId(), index, null);
            if (holdingItem) {
                session.setItem(player.getUniqueId(), index, cursor.clone());
                event.getView().setCursor(null);
                gui.playItemSound();
            }
            tradeService.giveOrDrop(player, offered);
            tradeService.playSound(player, Sound.ENTITY_ITEM_PICKUP);
            updateBothGUIs(session);
            return;
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
            player.sendMessage(ChatColor.YELLOW + "已取消输入");
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
                player.sendMessage(ChatColor.RED + "数值不能为负数！");
                reopenGUI(player);
                return;
            }
            
            TradeSession session = tradeService.getSession(player.getUniqueId());
            if (session == null) {
                player.sendMessage(ChatColor.RED + "交易已结束！");
                return;
            }
            
            if (inputType == InputType.MONEY) {
                // Read the provider once: a reload on the main thread may drop it while this async
                // handler runs (UltiKits/UltiTrade#26). Without a provider the amount stays unchanged.
                Economy currentEconomy = tradeService.getEconomy();
                if (currentEconomy == null || !config.isEnableMoneyTrade()) {
                    player.sendMessage(ChatColor.RED + "金币交易当前不可用！");
                    reopenGUI(player);
                    return;
                }
                // Check balance
                if (currentEconomy.getBalance(player) < value) {
                    player.sendMessage(ChatColor.RED + "余额不足！");
                    reopenGUI(player);
                    return;
                }
                session.setMoney(player.getUniqueId(), value);
                player.sendMessage(ChatColor.GREEN + "已设置交易金币: " + value);
            } else {
                // A reload may have turned experience trading off since the prompt opened
                // (UltiKits/UltiTrade#26). Without it the amount stays unchanged.
                if (!config.isEnableExpTrade()) {
                    player.sendMessage(ChatColor.RED + "经验交易当前不可用！");
                    reopenGUI(player);
                    return;
                }
                // Check experience
                int expValue = (int) value;
                if (tradeService.getTotalExperience(player) < expValue) {
                    player.sendMessage(ChatColor.RED + "经验不足！");
                    reopenGUI(player);
                    return;
                }
                session.setExp(player.getUniqueId(), expValue);
                player.sendMessage(ChatColor.GREEN + "已设置交易经验: " + expValue);
            }
            
            reopenGUI(player);
            
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "无效的数值！");
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
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TradeGUI ||
            event.getInventory().getHolder() instanceof TradeConfirmPage) {
            event.setCancelled(true);
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
}
