package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeRequest;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.gui.TradeGUI;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;

import com.cryptomorin.xseries.particles.XParticle;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing player trades.
 * Includes blacklist, toggle, sounds, particles, BossBar, and trade logging.
 *
 * @author wisdomme
 * @version 2.0.0
 */
@Service
public class TradeService {
    
    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private TradeConfig config;

    @Autowired
    private TradeLogService logService;
    
    // Pending trade requests
    private final Map<UUID, TradeRequest> pendingRequests = new ConcurrentHashMap<>();
    
    // Active trade sessions
    private final Map<UUID, TradeSession> activeSessions = new ConcurrentHashMap<>();
    
    // Player to session mapping
    private final Map<UUID, UUID> playerSessionMap = new ConcurrentHashMap<>();
    
    // BossBar for trade requests
    private final Map<UUID, BossBar> requestBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> bossBarTasks = new ConcurrentHashMap<>();
    
    // Bukkit plugin instance for scheduler tasks
    private Plugin bukkitPlugin;

    /**
     * Cancellation reason shown to both players when a trade that carries money reaches
     * completion while money trading is unavailable (UltiKits/UltiTrade#26).
     */
    static final String MONEY_UNAVAILABLE_REASON = "金币交易当前不可用";

    /** Cancellation reason when a trade that carries experience completes while experience trading is off. */
    static final String EXP_UNAVAILABLE_REASON = "经验交易当前不可用";

    /** Sent to both players of a trade whose confirmations a configuration reload voided. */
    static final String RECONFIRM_AFTER_RELOAD_MESSAGE = "交易配置已重载，请重新确认交易。";

    // Economy integration. Volatile: a reload replaces it on the main thread while the async chat
    // handler may read it; callers read it once per operation.
    private volatile Economy economy;
    
    /**
     * Initialize the trade service.
     */
    public void init() {
        // Initialize Bukkit plugin reference for scheduler tasks
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");

        // Setup economy
        if (config.isEnableMoneyTrade()) {
            setupEconomy();
        }
    }
    
    /**
     * Shutdown the service.
     */
    public void shutdown() {
        // Cancel all active sessions. Each is isolated: a failure while closing one trade must not
        // skip the trades after it, which is how one unschedulable log write used to destroy every
        // staked item on the server (UltiKits/UltiTrade#34).
        for (TradeSession session : activeSessions.values()) {
            try {
                cancelTrade(session, "插件关闭");
            } catch (RuntimeException e) {
                plugin.getLogger().warn(e, "Failed to cancel a trade during shutdown; continuing with the remaining trades.");
            }
        }
        
        // Cleanup BossBars
        for (BossBar bar : requestBossBars.values()) {
            bar.removeAll();
        }
        for (BukkitTask task : bossBarTasks.values()) {
            task.cancel();
        }
        
        pendingRequests.clear();
        activeSessions.clear();
        playerSessionMap.clear();
        requestBossBars.clear();
        bossBarTasks.clear();
    }
    
    /**
     * Setup Vault economy.
     */
    private void setupEconomy() {
        // The held provider is replaced by exactly what this lookup finds, including nothing, so a
        // provider that has gone is never kept. If the lookup itself throws, the previous provider
        // stays in place.
        Economy found = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warn("Vault not found! Money trading disabled.");
        } else {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                found = rsp.getProvider();
            } else {
                plugin.getLogger().warn("No Vault economy provider is registered! Money trading disabled.");
            }
        }
        economy = found;
    }
    
    /**
     * Reconcile the Vault economy provider with the current {@code enable-money-trade} value after
     * a configuration reload (UltiKits/UltiTrade#26). When money trading is enabled the provider
     * lookup is re-run and its result replaces the held provider (a provider that has gone is
     * dropped, a replaced one is adopted), so a {@code false} to {@code true} change takes effect
     * without a restart; when it is disabled the provider is dropped without a lookup or a log line.
     * The lookup registers nothing, so repeated reloads are safe.
     */
    public void reloadEconomy() {
        if (config.isEnableMoneyTrade()) {
            setupEconomy();
        } else {
            economy = null;
        }
    }

    /**
     * Void every confirmation in open trades after a configuration reload (UltiKits/UltiTrade#26).
     * A reload can change the terms a confirmation was given for ({@code trade-tax},
     * {@code exp-tax-rate}, {@code confirm-threshold}, which offers are allowed), so a trade must never
     * complete on a confirmation given before it. Both players of an affected trade are told to
     * confirm again.
     */
    public void resetConfirmationsAfterReload() {
        for (TradeSession session : activeSessions.values()) {
            UUID first = session.getPlayer1();
            UUID second = session.getPlayer2();
            if (!session.isConfirmed(first) && !session.isConfirmed(second)) {
                continue;
            }
            session.setConfirmed(first, false);
            session.setConfirmed(second, false);
            for (UUID participant : new UUID[] {first, second}) {
                Player player = Bukkit.getPlayer(participant);
                if (player != null) {
                    player.sendMessage(ChatColor.YELLOW + RECONFIRM_AFTER_RELOAD_MESSAGE);
                }
            }
        }
    }

    /**
     * Redraw every open trade window from the reloaded configuration (UltiKits/UltiTrade#27).
     * <p>
     * An open trade window shows values a reload can change: the title ({@code gui-title}),
     * money and experience availability, and the money and experience tax
     * ({@code trade-tax}, {@code exp-tax-rate}). A window is redrawn in place, which keeps it open
     * and so does not trigger the close-cancels-the-trade handling. An open large-trade
     * confirmation page, which also shows the taxes and {@code confirm-threshold}, is replaced by
     * a trade window built from the reloaded configuration. Offers are read from the session, so
     * nothing offered is added, removed or returned.
     * <p>
     * Each player's window is redrawn in isolation: a failure is logged at SEVERE with the player's
     * name and the remaining windows are still redrawn.
     */
    public void refreshOpenTradeWindowsAfterReload() {
        for (TradeSession session : activeSessions.values()) {
            for (UUID participant : new UUID[] {session.getPlayer1(), session.getPlayer2()}) {
                Player player = Bukkit.getPlayer(participant);
                if (player == null) {
                    continue;
                }
                try {
                    refreshOpenTradeWindow(session, player);
                } catch (RuntimeException | LinkageError e) {
                    plugin.getLogger().error(e, "Could not redraw the open trade window of " + player.getName()
                        + " after the reload; that window shows the previous terms until it is reopened");
                }
            }
        }
    }

    private void refreshOpenTradeWindow(TradeSession session, Player player) {
        InventoryView view = player.getOpenInventory();
        if (view == null || view.getTopInventory() == null) {
            return;
        }
        InventoryHolder holder = view.getTopInventory().getHolder();
        if (holder instanceof TradeGUI) {
            TradeGUI gui = (TradeGUI) holder;
            gui.update();
            retitle(view, gui.buildTitle());
        } else if (holder instanceof TradeConfirmPage) {
            TradeGUI gui = new TradeGUI(this, session, player);
            gui.update();
            player.openInventory(gui.getInventory());
        }
    }

    /**
     * Apply a new window title where the server supports it. {@code InventoryView#setTitle} does
     * not exist on older server versions; there the window's contents are still redrawn and only
     * its title keeps the previous {@code gui-title} until it is reopened.
     */
    private static void retitle(InventoryView view, String title) {
        try {
            view.setTitle(title);
        } catch (NoSuchMethodError | AbstractMethodError | UnsupportedOperationException e) {
            // Title changes are unsupported on this server; the redrawn contents already apply.
        }
    }

    /**
     * Check if economy is available.
     */
    public boolean hasEconomy() {
        Economy current = economy;
        return current != null && config.isEnableMoneyTrade();
    }
    
    /**
     * Get economy instance.
     */
    public Economy getEconomy() {
        return economy;
    }
    
    /**
     * Send a trade request.
     * 
     * @param sender Request sender
     * @param target Request target
     * @return true if request sent
     */
    public boolean sendRequest(Player sender, Player target) {
        // Check if sender has trade enabled
        if (!logService.isTradeEnabled(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "你已关闭交易功能！使用 /trade toggle 开启");
            return false;
        }
        
        // Check if target has trade enabled
        if (!logService.isTradeEnabled(target.getUniqueId())) {
            String msg = config.getTradeDisabledMessage().replace("{PLAYER}", target.getName());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }
        
        // Check if sender is blocked by target
        if (logService.isBlocked(target.getUniqueId(), sender.getUniqueId())) {
            String msg = config.getPlayerBlockedMessage().replace("{PLAYER}", target.getName());
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return false;
        }
        
        // Check if target is blocked by sender
        if (logService.isBlocked(sender.getUniqueId(), target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "你已将 " + target.getName() + " 加入黑名单！");
            return false;
        }
        
        // Check if sender is already trading
        if (isTrading(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "你已经在交易中！");
            return false;
        }
        
        // Check if target is already trading
        if (isTrading(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + target.getName() + " 正在交易中！");
            return false;
        }
        
        // Check distance
        if (config.getMaxDistance() > 0) {
            if (!config.isAllowCrossWorld() && !sender.getWorld().equals(target.getWorld())) {
                sender.sendMessage(ChatColor.RED + "不能跨世界交易！");
                return false;
            }
            
            if (sender.getWorld().equals(target.getWorld()) && 
                sender.getLocation().distance(target.getLocation()) > config.getMaxDistance()) {
                sender.sendMessage(ChatColor.RED + "距离太远，无法交易！");
                return false;
            }
        }
        
        // Check if there's already a pending request from sender
        TradeRequest existingRequest = pendingRequests.get(target.getUniqueId());
        if (existingRequest != null && existingRequest.getSender().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "你已经向该玩家发送过交易请求了！");
            return false;
        }
        
        // Check if target has sent a request to sender (auto-accept)
        TradeRequest reverseRequest = pendingRequests.get(sender.getUniqueId());
        if (reverseRequest != null && reverseRequest.getSender().equals(target.getUniqueId())) {
            removeBossBar(sender.getUniqueId());
            pendingRequests.remove(sender.getUniqueId());
            startTrade(target, sender);
            return true;
        }
        
        // Create and store request
        // The timeout is promised to the receiver now; a later reload applies only to new requests.
        TradeRequest request = new TradeRequest(sender.getUniqueId(), target.getUniqueId(), config.getRequestTimeout());
        pendingRequests.put(target.getUniqueId(), request);
        
        // Notify sender
        String sentMsg = config.getRequestSentMessage().replace("{PLAYER}", target.getName());
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', sentMsg));
        playSound(sender, Sound.BLOCK_NOTE_BLOCK_PLING);
        
        // Notify target
        notifyTradeRequest(target, sender);
        
        // Show BossBar if enabled
        if (config.isEnableBossbar()) {
            showRequestBossBar(target, sender.getName(), request.getTimeoutSeconds());
        }
        
        return true;
    }
    
    /**
     * Notify player of trade request with clickable buttons.
     */
    private void notifyTradeRequest(Player target, Player sender) {
        if (config.isEnableClickableButtons()) {
            // Create clickable message
            TextComponent message = new TextComponent(ChatColor.YELLOW + sender.getName() + 
                ChatColor.WHITE + " 请求与你交易！ ");
            
            // Accept button
            TextComponent acceptBtn = new TextComponent(ChatColor.GREEN + "[接受]");
            acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"));
            acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(ChatColor.GREEN + "点击接受交易请求")));
            
            // Deny button
            TextComponent denyBtn = new TextComponent(ChatColor.RED + " [拒绝]");
            denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"));
            denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(ChatColor.RED + "点击拒绝交易请求")));
            
            message.addExtra(acceptBtn);
            message.addExtra(denyBtn);
            
            target.spigot().sendMessage(message);
        } else {
            String receivedMsg = config.getRequestReceivedMessage().replace("{PLAYER}", sender.getName());
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', receivedMsg));
        }
        
        playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL);
    }
    
    /**
     * Show BossBar for trade request countdown.
     */
    private void showRequestBossBar(Player target, String senderName, int timeoutSeconds) {
        // Remove existing BossBar if any
        removeBossBar(target.getUniqueId());
        
        BossBar bar = Bukkit.createBossBar(
            ChatColor.YELLOW + senderName + " 请求与你交易 (剩余 " + timeoutSeconds + "秒)",
            BarColor.YELLOW,
            BarStyle.SOLID
        );
        bar.addPlayer(target);
        bar.setProgress(1.0);
        requestBossBars.put(target.getUniqueId(), bar);
        
        // Start countdown task
        final int[] remaining = {timeoutSeconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(bukkitPlugin, () -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                removeBossBar(target.getUniqueId());
                return;
            }
            
            double progress = (double) remaining[0] / timeoutSeconds;
            bar.setProgress(Math.max(0, progress));
            bar.setTitle(ChatColor.YELLOW + senderName + " 请求与你交易 (剩余 " + remaining[0] + "秒)");
            
            // Change color when time is running out
            if (remaining[0] <= 5) {
                bar.setColor(BarColor.RED);
            } else if (remaining[0] <= 10) {
                bar.setColor(BarColor.PINK);
            }
        }, 20L, 20L);
        
        bossBarTasks.put(target.getUniqueId(), task);
    }
    
    /**
     * Remove BossBar for player.
     */
    private void removeBossBar(UUID playerUuid) {
        BossBar bar = requestBossBars.remove(playerUuid);
        if (bar != null) {
            bar.removeAll();
        }
        
        BukkitTask task = bossBarTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * Accept a trade request.
     * 
     * @param player Player accepting
     * @return true if accepted
     */
    public boolean acceptRequest(Player player) {
        removeBossBar(player.getUniqueId());
        
        TradeRequest request = pendingRequests.remove(player.getUniqueId());
        if (request == null || request.isExpired()) {
            player.sendMessage(ChatColor.RED + "没有待处理的交易请求！");
            return false;
        }
        
        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender == null || !sender.isOnline()) {
            player.sendMessage(ChatColor.RED + "对方已离线！");
            return false;
        }
        
        startTrade(sender, player);
        return true;
    }
    
    /**
     * Deny a trade request.
     * 
     * @param player Player denying
     * @return true if denied
     */
    public boolean denyRequest(Player player) {
        removeBossBar(player.getUniqueId());
        
        TradeRequest request = pendingRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage(ChatColor.RED + "没有待处理的交易请求！");
            return false;
        }
        
        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(ChatColor.RED + player.getName() + " 拒绝了你的交易请求！");
            playSound(sender, Sound.ENTITY_VILLAGER_NO);
        }
        
        player.sendMessage(ChatColor.YELLOW + "已拒绝交易请求！");
        return true;
    }
    
    /**
     * Start a trade between two players.
     */
    public void startTrade(Player player1, Player player2) {
        TradeSession session = new TradeSession(player1, player2);
        
        activeSessions.put(session.getSessionId(), session);
        playerSessionMap.put(player1.getUniqueId(), session.getSessionId());
        playerSessionMap.put(player2.getUniqueId(), session.getSessionId());
        
        // Open trade GUI for both players
        TradeGUI gui1 = new TradeGUI(this, session, player1);
        TradeGUI gui2 = new TradeGUI(this, session, player2);
        
        player1.openInventory(gui1.getInventory());
        player2.openInventory(gui2.getInventory());
        
        // Play sound
        playSound(player1, Sound.BLOCK_CHEST_OPEN);
        playSound(player2, Sound.BLOCK_CHEST_OPEN);
    }
    
    /**
     * Get active session for player.
     */
    public TradeSession getSession(UUID playerUuid) {
        UUID sessionId = playerSessionMap.get(playerUuid);
        if (sessionId == null) {
            return null;
        }
        return activeSessions.get(sessionId);
    }
    
    /**
     * Check if player is in trade.
     */
    public boolean isTrading(UUID playerUuid) {
        return playerSessionMap.containsKey(playerUuid);
    }
    
    /**
     * Confirm trade for player.
     * Handles large trade confirmation if threshold is exceeded.
     */
    public void confirmTrade(Player player) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        
        // Check if large trade confirmation is needed
        double threshold = config.getConfirmThreshold();
        double totalMoney = session.getPlayerMoney(player.getUniqueId()) + 
                           session.getOtherPlayerMoney(player.getUniqueId());
        int totalExp = session.getPlayerExp(player.getUniqueId()) + 
                       session.getOtherPlayerExp(player.getUniqueId());
        
        // If already confirmed once (in session), proceed
        if (!session.isConfirmed(player.getUniqueId()) && 
            (totalMoney >= threshold || totalExp >= threshold)) {
            // Show confirmation page
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(bukkitPlugin, () -> {
                TradeConfirmPage confirmPage = new TradeConfirmPage(
                    this, session, player,
                    () -> {
                        // On confirm - mark as confirmed and reopen trade GUI
                        session.setConfirmed(player.getUniqueId(), true);
                        notifyConfirmation(session, player);
                        
                        // Reopen trade GUI
                        Bukkit.getScheduler().runTaskLater(bukkitPlugin, () -> {
                            if (isTrading(player.getUniqueId())) {
                                TradeGUI gui = new TradeGUI(this, session, player);
                                player.openInventory(gui.getInventory());
                            }
                        }, 1L);
                    },
                    () -> {
                        // On cancel - reopen trade GUI
                        Bukkit.getScheduler().runTaskLater(bukkitPlugin, () -> {
                            if (isTrading(player.getUniqueId())) {
                                TradeGUI gui = new TradeGUI(this, session, player);
                                player.openInventory(gui.getInventory());
                            }
                        }, 1L);
                    }
                );
                confirmPage.open();
            }, 1L);
            return;
        }
        
        session.setConfirmed(player.getUniqueId(), true);
        notifyConfirmation(session, player);
        
        // Check if both confirmed
        if (session.isBothConfirmed()) {
            completeTrade(session);
        }
    }
    
    /**
     * Notify other player of confirmation.
     */
    private void notifyConfirmation(TradeSession session, Player confirmer) {
        Player other = Bukkit.getPlayer(session.getOtherPlayer(confirmer.getUniqueId()));
        if (other != null) {
            other.sendMessage(ChatColor.GREEN + confirmer.getName() + " 已确认交易！");
            playSound(other, Sound.BLOCK_NOTE_BLOCK_PLING);
        }
    }
    
    /**
     * Cancel confirmation.
     */
    public void cancelConfirmation(Player player) {
        TradeSession session = getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        
        session.setConfirmed(player.getUniqueId(), false);
    }
    
    /**
     * Complete the trade.
     */
    public void completeTrade(TradeSession session) {
        Player player1 = Bukkit.getPlayer(session.getPlayer1());
        Player player2 = Bukkit.getPlayer(session.getPlayer2());
        
        if (player1 == null || player2 == null) {
            cancelTrade(session, "玩家离线");
            return;
        }
        
        double moneyTax = 0;
        int expTax = 0;

        double money1 = session.getPlayerMoney(session.getPlayer1());
        double money2 = session.getPlayerMoney(session.getPlayer2());
        // Read the provider once: a reload may replace it at any time.
        Economy currentEconomy = economy;
        boolean moneyAvailable = currentEconomy != null && config.isEnableMoneyTrade();

        // Never move items or experience while silently dropping offered money (UltiKits/UltiTrade#26):
        // money is only withdrawn here, so cancelling leaves every balance untouched and returns items.
        if ((money1 > 0 || money2 > 0) && !moneyAvailable) {
            cancelTrade(session, MONEY_UNAVAILABLE_REASON);
            return;
        }

        // The same rule for experience: an offer is never dropped while the items still move.
        int exp1 = session.getPlayerExp(session.getPlayer1());
        int exp2 = session.getPlayerExp(session.getPlayer2());
        boolean expAvailable = config.isEnableExpTrade();
        if ((exp1 > 0 || exp2 > 0) && !expAvailable) {
            cancelTrade(session, EXP_UNAVAILABLE_REASON);
            return;
        }

        // Handle money transfer
        if (moneyAvailable) {

            // Apply tax
            double taxRate = config.getTradeTax();
            double tax1 = money1 * taxRate;
            double tax2 = money2 * taxRate;
            moneyTax = tax1 + tax2;
            
            // Check balances
            if (money1 > 0 && currentEconomy.getBalance(player1) < money1) {
                cancelTrade(session, player1.getName() + " 余额不足");
                return;
            }
            if (money2 > 0 && currentEconomy.getBalance(player2) < money2) {
                cancelTrade(session, player2.getName() + " 余额不足");
                return;
            }
            
            // Transfer money
            if (money1 > 0) {
                currentEconomy.withdrawPlayer(player1, money1);
                currentEconomy.depositPlayer(player2, money1 - tax1);
            }
            if (money2 > 0) {
                currentEconomy.withdrawPlayer(player2, money2);
                currentEconomy.depositPlayer(player1, money2 - tax2);
            }
        }
        
        // Handle experience transfer
        if (expAvailable) {
            
            // Apply tax
            double expTaxRate = config.getExpTaxRate();
            int tax1 = (int)(exp1 * expTaxRate);
            int tax2 = (int)(exp2 * expTaxRate);
            expTax = tax1 + tax2;
            
            // Check experience
            if (exp1 > 0 && getTotalExperience(player1) < exp1) {
                cancelTrade(session, player1.getName() + " 经验不足");
                return;
            }
            if (exp2 > 0 && getTotalExperience(player2) < exp2) {
                cancelTrade(session, player2.getName() + " 经验不足");
                return;
            }
            
            // Transfer experience
            if (exp1 > 0) {
                setTotalExperience(player1, getTotalExperience(player1) - exp1);
                player2.giveExp(exp1 - tax1);
            }
            if (exp2 > 0) {
                setTotalExperience(player2, getTotalExperience(player2) - exp2);
                player1.giveExp(exp2 - tax2);
            }
        }
        
        // Transfer items
        Map<Integer, ItemStack> items1 = session.getPlayerItems(session.getPlayer1());
        Map<Integer, ItemStack> items2 = session.getPlayerItems(session.getPlayer2());
        
        // Give player1's items to player2
        for (ItemStack item : items1.values()) {
            if (item != null) {
                giveOrDrop(player2, item);
            }
        }
        
        // Give player2's items to player1
        for (ItemStack item : items2.values()) {
            if (item != null) {
                giveOrDrop(player1, item);
            }
        }
        
        // Close inventories
        player1.closeInventory();
        player2.closeInventory();
        
        session.setState(TradeSession.TradeState.COMPLETED);
        cleanupSession(session);

        // After the state change and the cleanup, so that a failure here cannot leave a paid trade
        // that the pending close event can still cancel and refund a second time (UltiKits/UltiTrade#34).
        logService.logCompletedTrade(session, player1, player2, moneyTax, expTax);
        
        // Notify players
        String completeMsg = ChatColor.translateAlternateColorCodes('&', config.getTradeCompleteMessage());
        player1.sendMessage(completeMsg);
        player2.sendMessage(completeMsg);
        
        // Play success effects
        playSuccessEffects(player1);
        playSuccessEffects(player2);
    }
    
    /**
     * Cancel a trade.
     */
    public void cancelTrade(TradeSession session, String reason) {
        Player player1 = Bukkit.getPlayer(session.getPlayer1());
        Player player2 = Bukkit.getPlayer(session.getPlayer2());
        
        // Return items to original owners. Nothing that can fail runs before this: the staked items
        // are the players' property, and the trade log is a courtesy. This used to start with the log
        // call, which schedules an asynchronous write — and during server shutdown the scheduler
        // rejects a task for the already-disabled plugin, so the throw left this method before a
        // single item was returned and aborted shutdown's loop over the remaining trades
        // (UltiKits/UltiTrade#34). The log now runs at the end, where its failure costs nothing.
        if (player1 != null) {
            for (ItemStack item : session.getPlayerItems(session.getPlayer1()).values()) {
                if (item != null) {
                    giveOrDrop(player1, item);
                }
            }
            player1.closeInventory();
            String msg = config.getTradeCancelledMessage();
            if (reason != null) {
                msg += " (" + reason + ")";
            }
            player1.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            playFailEffects(player1);
        }
        
        if (player2 != null) {
            for (ItemStack item : session.getPlayerItems(session.getPlayer2()).values()) {
                if (item != null) {
                    giveOrDrop(player2, item);
                }
            }
            player2.closeInventory();
            String msg = config.getTradeCancelledMessage();
            if (reason != null) {
                msg += " (" + reason + ")";
            }
            player2.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            playFailEffects(player2);
        }
        
        session.setState(TradeSession.TradeState.CANCELLED);
        cleanupSession(session);

        // Last, once every item is back with its owner and the session is closed.
        logService.logCancelledTrade(session, reason);
    }
    
    /**
     * Cancel trade by player.
     */
    public void cancelTrade(Player player) {
        TradeSession session = getSession(player.getUniqueId());
        if (session != null) {
            cancelTrade(session, player.getName() + " 取消了交易");
        }
    }
    
    /**
     * Give an item to a player, dropping at the player's own feet whatever the inventory cannot
     * hold.
     * <p>
     * {@link org.bukkit.inventory.Inventory#addItem(ItemStack...)} returns the stacks it could not
     * store. Discarding that return value destroys them, which is what UltiKits/UltiTrade#20
     * reported for the trade window's remove-item click. Every path in this module that hands an
     * item back to a player goes through this one method, so no call site can discard the leftover
     * again.
     *
     * @param player the player to give the item to, and at whose location any overflow is dropped
     * @param item   the item to give
     */
    public void giveOrDrop(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    /**
     * Cleanup session.
     */
    private void cleanupSession(TradeSession session) {
        activeSessions.remove(session.getSessionId());
        playerSessionMap.remove(session.getPlayer1());
        playerSessionMap.remove(session.getPlayer2());
    }
    
    /**
     * Cleanup expired requests every 10 seconds.
     * Scheduled task using @Scheduled annotation.
     */
    @Scheduled(period = 200, async = false)
    public void cleanupExpiredRequests() {
        Iterator<Map.Entry<UUID, TradeRequest>> it = pendingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TradeRequest> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
                removeBossBar(entry.getKey());
                
                // Notify receiver
                Player receiver = Bukkit.getPlayer(entry.getKey());
                if (receiver != null) {
                    receiver.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                        config.getRequestTimeoutMessage()));
                }
            }
        }
    }
    
    // ==================== Sound and Particle Effects ====================
    
    /**
     * Play a sound to player.
     */
    public void playSound(Player player, Sound sound) {
        if (config.isEnableSounds() && player != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }
    
    /**
     * Play success effects (sound + particles).
     */
    private void playSuccessEffects(Player player) {
        if (player == null) return;
        
        playSound(player, Sound.ENTITY_PLAYER_LEVELUP);
        
        if (config.isEnableParticles()) {
            Location loc = player.getLocation().add(0, 1, 0);
            Particle happyVillager = XParticle.HAPPY_VILLAGER.get();
            if (happyVillager != null) {
                player.getWorld().spawnParticle(happyVillager, loc, 30, 0.5, 0.5, 0.5, 0.1);
            } else {
                plugin.getLogger().warn("XParticle.HAPPY_VILLAGER resolved to null on this server version; skipping success particle effect.");
            }
            player.getWorld().spawnParticle(Particle.END_ROD, loc, 15, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /**
     * Play fail effects (sound + particles).
     */
    private void playFailEffects(Player player) {
        if (player == null) return;

        playSound(player, Sound.ENTITY_VILLAGER_NO);

        if (config.isEnableParticles()) {
            Location loc = player.getLocation().add(0, 1, 0);
            Particle smoke = XParticle.SMOKE.get();
            if (smoke != null) {
                player.getWorld().spawnParticle(smoke, loc, 20, 0.3, 0.3, 0.3, 0.05);
            } else {
                plugin.getLogger().warn("XParticle.SMOKE resolved to null on this server version; skipping fail particle effect.");
            }
        }
    }
    
    // ==================== Experience Utilities ====================
    
    /**
     * Get total experience points for a player.
     */
    public int getTotalExperience(Player player) {
        int level = player.getLevel();
        int exp = (int) (player.getExp() * player.getExpToLevel());
        
        // Calculate total exp from levels
        int totalFromLevels;
        if (level <= 16) {
            totalFromLevels = level * level + 6 * level;
        } else if (level <= 31) {
            totalFromLevels = (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            totalFromLevels = (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        
        return totalFromLevels + exp;
    }
    
    /**
     * Set total experience points for a player.
     */
    public void setTotalExperience(Player player, int totalExp) {
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        
        if (totalExp > 0) {
            player.giveExp(totalExp);
        }
    }
    
    /**
     * Get config.
     */
    public TradeConfig getConfig() {
        return config;
    }
    
    /**
     * Get log service.
     */
    public TradeLogService getLogService() {
        return logService;
    }
}
