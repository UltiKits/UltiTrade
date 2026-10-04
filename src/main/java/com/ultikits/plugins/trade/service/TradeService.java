package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.PendingStakeReturn;
import com.ultikits.plugins.trade.entity.TradeRequest;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.gui.TradeGUI;
import com.ultikits.plugins.trade.util.Placeholders;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Scheduled;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.entities.WhereCondition;
import com.ultikits.ultitools.interfaces.DataOperator;

import com.cryptomorin.xseries.particles.XParticle;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.Inventory;
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
    
    /** Players between closing their trade window and seeing the confirmation page (UltiKits/UltiTrade#23). */
    private final Set<UUID> confirmPageTransitions = ConcurrentHashMap.newKeySet();

    // Bukkit plugin instance for scheduler tasks
    private Plugin bukkitPlugin;

    // Stakes of cancelled trades whose owner could not be found, handed over at their next join
    // (UltiKits/UltiTrade#32). Null until init() has run, which the save path treats as a failed save.
    private DataOperator<PendingStakeReturn> pendingReturns;

    // Economy integration. Volatile: a reload replaces it on the main thread while the async chat
    // handler may read it; callers read it once per operation. Held as Object, not Economy: this
    // module declares NEITHER a hard depend NOR a softdepend on Vault in plugin.yml, so nothing
    // guarantees Vault is present when the framework autowires this @Service bean and scans it for
    // @Scheduled methods (both walk Class#getDeclaredFields()/getDeclaredMethods(), which eagerly
    // resolve every declared type) -- a field or method return type of Economy directly would throw
    // NoClassDefFoundError and fail the whole module on a server without Vault, the same crash shape
    // as UltiKits/UltiTrade#48's PlaceholderAPI field, just for a different soft dependency.
    private volatile Object economy;
    
    /**
     * This module's language-file text for {@code key}, in the server's language. The GUIs, the
     * listener and the placeholder expansion reach the language file through this service
     * (UltiKits/UltiTrade#16). Without an injected plugin there is no language to read, and the key
     * itself is returned, as the framework renders a missing key: a trade cancelled during shutdown
     * must still hand its items back when this service never received a plugin (UltiKits/UltiTrade#34).
     *
     * @param key the language-file key
     * @return the text for the key
     */
    public String i18n(String key) {
        return plugin == null ? key : plugin.i18n(key);
    }

    /** {@link #i18n} with {@code &} colour codes applied. */
    private String text(String languageText) {
        return ChatColor.translateAlternateColorCodes('&', languageText);
    }

    /**
     * Initialize the trade service.
     */
    public void init() {
        // Initialize Bukkit plugin reference for scheduler tasks
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");

        // Saved stakes of cancelled trades (UltiKits/UltiTrade#32)
        this.pendingReturns = plugin.getDataOperator(PendingStakeReturn.class);

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
                cancelTrade(session, i18n("cancel_reason_shutdown"));
            } catch (RuntimeException e) {
                // A handler whose only job is to stop one failure costing the other trades their items
                // must not be able to throw itself. The injected plugin is the only thing it needs, and
                // a service that never received one would otherwise turn this rescue into the very
                // abort it exists to prevent (measured: this line raised a NullPointerException from
                // inside the catch).
                if (plugin != null) {
                    plugin.getLogger().warn(e, i18n("log_shutdown_cancel_failed"));
                }
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
            plugin.getLogger().warn(i18n("log_vault_missing"));
        } else {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                found = rsp.getProvider();
            } else {
                plugin.getLogger().warn(i18n("log_no_economy_provider"));
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
     * Withdraws, from every open trade, the money and experience offers a reload has made
     * unavailable, and tells both players (UltiKits/UltiTrade#28).
     * <p>
     * Before, such an offer could not be withdrawn at all -- the money slot needs money trading, and
     * the prompt refused every amount -- so the players could only cancel and start again; the trade
     * was then cancelled at completion anyway. Withdrawing an offer takes nothing from anybody: money
     * and experience move only when a trade completes. Setting an offer also resets both
     * confirmations, and the windows are redrawn by {@link #refreshOpenTradeWindowsAfterReload()},
     * which the reload runs after this. Called from {@link #resetConfirmationsAfterReload()}.
     */
    void withdrawUnavailableOffersAfterReload() {
        boolean moneyAvailable = hasEconomy();
        boolean expAvailable = config.isEnableExpTrade();
        for (TradeSession session : activeSessions.values()) {
            UUID first = session.getPlayer1();
            UUID second = session.getPlayer2();
            boolean moneyWithdrawn = false;
            boolean expWithdrawn = false;
            for (UUID participant : new UUID[] {first, second}) {
                if (!moneyAvailable && session.getPlayerMoney(participant) != 0) {
                    session.setMoney(participant, 0);
                    moneyWithdrawn = true;
                }
                if (!expAvailable && session.getPlayerExp(participant) != 0) {
                    session.setExp(participant, 0);
                    expWithdrawn = true;
                }
            }
            for (UUID participant : new UUID[] {first, second}) {
                Player player = Bukkit.getPlayer(participant);
                if (player == null) {
                    continue;
                }
                if (moneyWithdrawn) {
                    player.sendMessage(text(i18n("offer_withdrawn_money_unavailable")));
                }
                if (expWithdrawn) {
                    player.sendMessage(text(i18n("offer_withdrawn_exp_unavailable")));
                }
            }
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
        // First withdraw the offers the reload made unavailable; that resets those trades'
        // confirmations as well (UltiKits/UltiTrade#28).
        withdrawUnavailableOffersAfterReload();
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
                    player.sendMessage(text(i18n("reconfirm_after_reload")));
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
     * name and the remaining windows are still redrawn. The players whose window could not be redrawn
     * are returned, so the reload can report itself as partial (UltiKits/UltiTrade#50).
     *
     * @return the names of the players whose window still shows the previous terms, in the order tried;
     *         empty when every open window was redrawn
     */
    public List<String> refreshOpenTradeWindowsAfterReload() {
        List<String> notRedrawn = new ArrayList<>();
        for (TradeSession session : activeSessions.values()) {
            for (UUID participant : new UUID[] {session.getPlayer1(), session.getPlayer2()}) {
                Player player = Bukkit.getPlayer(participant);
                if (player == null) {
                    continue;
                }
                try {
                    refreshOpenTradeWindow(session, player);
                } catch (RuntimeException | LinkageError e) {
                    plugin.getLogger().error(e, i18n("log_window_redraw_failed").replace("{PLAYER}", player.getName()));
                    notRedrawn.add(player.getName());
                }
            }
        }
        return notRedrawn;
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
            // A new window renders the session itself (UltiKits/UltiTrade#35). The page is retired first:
            // this replacement is not the player closing it, so its Cancel ("back") must not run.
            ((TradeConfirmPage) holder).dismiss();
            try {
                TradeGUI gui = new TradeGUI(this, session, player);
                // The page is already dismissed and inert -- its buttons and Esc now do nothing -- so
                // if opening the replacement is refused (openOrCancel's own null-return case) or
                // building/opening it throws (caught below), the player would otherwise be left
                // staring at a dead window while the trade keeps running with both stakes locked,
                // recoverable only by someone noticing and cancelling it by hand. openOrCancel cancels
                // the trade itself on a refusal; a thrown exception is cancelled here, then rethrown so
                // the caller's own SEVERE log for this player's failed redraw still fires
                // (UltiKits/UltiTrade#47 review).
                openOrCancel(player, session, gui.getInventory());
            } catch (RuntimeException | LinkageError e) {
                cancelTrade(session, i18n("cancel_reason_reload_window_failed"));
                throw e;
            }
        }
    }

    /**
     * Opens {@code inventory} for {@code player}, and cancels {@code session} instead of leaving the
     * player with no trade UI at all if another plugin refuses the {@code InventoryOpenEvent} this
     * fires. {@code HumanEntity#openInventory} is {@code @Nullable}: a refused open returns {@code
     * null} rather than throwing, which is easy to miss because nothing crashes -- the trade would
     * otherwise keep running with both stakes locked, recoverable only by someone noticing and
     * cancelling it by hand (UltiKits/UltiTrade#47 review). Every place in this class and
     * {@code TradeListener} that reopens or first opens a {@link TradeGUI} for a still-running session
     * goes through this one method, rather than checking the return value itself.
     *
     * @return true if the inventory actually opened; false if another plugin refused it (in which case
     *         {@code session} has already been cancelled)
     */
    public boolean openOrCancel(Player player, TradeSession session, Inventory inventory) {
        if (player.openInventory(inventory) != null) {
            return true;
        }
        cancelTrade(session, i18n("cancel_reason_window_refused"));
        return false;
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
     * Marks {@code player}'s large-trade confirmation page as answered, if one is currently open,
     * before this service closes their inventory itself (in {@link #cancelTrade} or
     * {@link #completeTrade}).
     * <p>
     * Paper reports the identical {@code InventoryCloseEvent.Reason.PLUGIN} whether this
     * {@code closeInventory()} call causes the close or an unrelated plugin's does, so the listener
     * cannot tell the two apart from the reason alone; {@link TradeConfirmPage#isAnswered()} lets it,
     * because every close this service or the page itself causes marks the page first. Left unmarked,
     * the listener would (correctly) treat an unexplained {@code PLUGIN} close as terminal and try to
     * cancel a trade this call is already ending -- redundant here, since {@code isCurrentSession}-style
     * checks make a second {@code cancelTrade} on an already-cleaned-up session a no-op, but worth
     * avoiding rather than relying on (UltiKits/UltiTrade#47 review).
     */
    private static void dismissConfirmPageIfOpen(Player player) {
        if (player == null) {
            return;
        }
        InventoryView view = player.getOpenInventory();
        if (view != null && view.getTopInventory() != null
                && view.getTopInventory().getHolder() instanceof TradeConfirmPage) {
            ((TradeConfirmPage) view.getTopInventory().getHolder()).dismiss();
        }
    }

    /**
     * Check if economy is available.
     */
    public boolean hasEconomy() {
        Object current = economy;
        return current != null && config.isEnableMoneyTrade();
    }
    
    /**
     * Get economy instance. Returned as Object, not Economy -- see the field's own comment; the one
     * production caller ({@code TradeListener}) casts it back after checking it is non-null.
     */
    public Object getEconomy() {
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
            sender.sendMessage(text(i18n("sender_trade_disabled")));
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
            sender.sendMessage(text(i18n("you_blocked_target").replace("{PLAYER}", target.getName())));
            return false;
        }
        
        // Check if sender is already trading
        if (isTrading(sender.getUniqueId())) {
            sender.sendMessage(text(i18n("already_trading")));
            return false;
        }
        
        // Check if target is already trading
        if (isTrading(target.getUniqueId())) {
            sender.sendMessage(text(i18n("target_trading").replace("{PLAYER}", target.getName())));
            return false;
        }
        
        // Check distance
        if (config.getMaxDistance() > 0) {
            if (!config.isAllowCrossWorld() && !sender.getWorld().equals(target.getWorld())) {
                sender.sendMessage(text(i18n("cross_world_disabled")));
                return false;
            }
            
            if (sender.getWorld().equals(target.getWorld()) && 
                sender.getLocation().distance(target.getLocation()) > config.getMaxDistance()) {
                sender.sendMessage(text(i18n("too_far_away")));
                return false;
            }
        }
        
        // Check if there's already a pending request from sender
        TradeRequest existingRequest = pendingRequests.get(target.getUniqueId());
        if (existingRequest != null && existingRequest.getSender().equals(sender.getUniqueId())) {
            sender.sendMessage(text(i18n("request_already_sent")));
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
            TextComponent message = new TextComponent(
                text(i18n("request_received_clickable").replace("{PLAYER}", sender.getName())));
            
            // Accept button
            TextComponent acceptBtn = new TextComponent(text(i18n("request_accept_button")));
            acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"));
            acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(text(i18n("request_accept_hover")))));
            
            // Deny button
            TextComponent denyBtn = new TextComponent(text(i18n("request_deny_button")));
            denyBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"));
            denyBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new Text(text(i18n("request_deny_hover")))));
            
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
            bossBarTitle(senderName, timeoutSeconds),
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
            bar.setTitle(bossBarTitle(senderName, remaining[0]));
            
            // Change color when time is running out
            if (remaining[0] <= 5) {
                bar.setColor(BarColor.RED);
            } else if (remaining[0] <= 10) {
                bar.setColor(BarColor.PINK);
            }
        }, 20L, 20L);
        
        bossBarTasks.put(target.getUniqueId(), task);
    }
    
    /** The request countdown's title, in the server's language. */
    private String bossBarTitle(String senderName, int secondsLeft) {
        return text(i18n("bossbar_request")
            .replace("{PLAYER}", senderName)
            .replace("{SECONDS}", String.valueOf(secondsLeft)));
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
            player.sendMessage(text(i18n("no_pending_request")));
            return false;
        }
        
        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender == null || !sender.isOnline()) {
            player.sendMessage(text(i18n("player_offline")));
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
            player.sendMessage(text(i18n("no_pending_request")));
            return false;
        }
        
        Player sender = Bukkit.getPlayer(request.getSender());
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(text(i18n("request_denied").replace("{PLAYER}", player.getName())));
            playSound(sender, Sound.ENTITY_VILLAGER_NO);
        }
        
        player.sendMessage(text(i18n("request_denied_self")));
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
        
        // Open trade GUI for both players. Neither has staked anything yet, so a refused open just
        // cancels this freshly-created, still-empty session (UltiKits/UltiTrade#47 review) rather than
        // leaving one player looking at a trade window for a trade the other side never really joined.
        // player2's window is not even attempted once player1's is refused: cancelTrade already closes
        // whichever of the two opened.
        TradeGUI gui1 = new TradeGUI(this, session, player1);
        TradeGUI gui2 = new TradeGUI(this, session, player2);

        if (!openOrCancel(player1, session, gui1.getInventory())) {
            return;
        }
        if (!openOrCancel(player2, session, gui2.getInventory())) {
            return;
        }

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
            openConfirmPage(session, player);
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
     * Whether {@code playerUuid} is between closing their trade window and seeing the large-trade
     * confirmation page. Closing the trade window is what cancels a trade, so the listener skips that
     * for this one close (UltiKits/UltiTrade#23).
     *
     * @param playerUuid the player
     * @return true while the confirmation page is being opened for them
     */
    public boolean isOpeningConfirmPage(UUID playerUuid) {
        return confirmPageTransitions.contains(playerUuid);
    }

    /**
     * Replaces {@code player}'s trade window with the large-trade confirmation page.
     * <p>
     * The window has to be closed first, and closing a trade window cancels the trade; the close used
     * to schedule that cancellation ahead of the page, so every large trade was cancelled the moment
     * its player clicked Confirm (UltiKits/UltiTrade#23). The player is marked as opening the page
     * before the close, and the mark is removed when the scheduled open runs -- whatever it then
     * finds, so it never outlives this transition. The page opens only if the player is still in this
     * same, running trade.
     */
    private void openConfirmPage(TradeSession session, Player player) {
        UUID uuid = player.getUniqueId();
        confirmPageTransitions.add(uuid);
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(bukkitPlugin, () -> {
            confirmPageTransitions.remove(uuid);
            if (!isCurrentSession(uuid, session)) {
                return;
            }
            int shownRevision = session.getRevision();
            boolean opened = new TradeConfirmPage(
                this, session, player,
                () -> confirmFromPage(session, player, shownRevision),
                () -> reopenTradeWindow(session, player)
            ).open();
            if (!opened) {
                // Another plugin refused the InventoryOpenEvent this fired: the trade window this page
                // was meant to replace is already closed (above), so the player is otherwise left with
                // no trade UI at all while the session keeps running with both stakes locked. Sent back
                // to the trade window exactly as clicking the page's own Cancel would (UltiKits/UltiTrade
                // #47 review) -- not cancelled outright, since the player did nothing wrong here and
                // nothing about their offer changed.
                reopenTradeWindow(session, player);
            }
        }, 1L);
    }

    /**
     * The confirmation page's Confirm button: marks {@code player} confirmed on the offer the page
     * showed. If either offer changed while the page was open, nothing is confirmed and the player is
     * sent back to the trade window to look again, because the page's figures are no longer the trade.
     */
    private void confirmFromPage(TradeSession session, Player player, int shownRevision) {
        UUID uuid = player.getUniqueId();
        if (!isCurrentSession(uuid, session)) {
            return;
        }
        if (session.getRevision() != shownRevision) {
            player.sendMessage(text(i18n("confirm_page_offer_changed")));
            reopenTradeWindow(session, player);
            return;
        }
        session.setConfirmed(uuid, true);
        notifyConfirmation(session, player);
        // The same completion the below-threshold path performs: a large trade used to stop here with
        // both players confirmed and never complete (UltiKits/UltiTrade#21).
        if (session.isBothConfirmed()) {
            completeTrade(session);
            return;
        }
        reopenTradeWindow(session, player);
        repaintOpenTradeWindows(session);
    }

    /** Opens a fresh trade window for {@code player} on the next tick, if they are still in this trade. */
    private void reopenTradeWindow(TradeSession session, Player player) {
        Bukkit.getScheduler().runTaskLater(bukkitPlugin, () -> {
            if (isCurrentSession(player.getUniqueId(), session)) {
                TradeGUI gui = new TradeGUI(this, session, player);
                openOrCancel(player, session, gui.getInventory());
            }
        }, 1L);
    }

    /** Redraws every trade window of this trade that is currently open. */
    private void repaintOpenTradeWindows(TradeSession session) {
        for (UUID participant : new UUID[] {session.getPlayer1(), session.getPlayer2()}) {
            Player participantPlayer = Bukkit.getPlayer(participant);
            if (participantPlayer == null) {
                continue;
            }
            InventoryView view = participantPlayer.getOpenInventory();
            if (view != null && view.getTopInventory() != null
                    && view.getTopInventory().getHolder() instanceof TradeGUI) {
                ((TradeGUI) view.getTopInventory().getHolder()).update();
            }
        }
    }

    /**
     * Whether {@code session} is still {@code playerUuid}'s running trade. A delayed task decides from
     * this, never from whether the player is trading at all: by the time it runs the player may be in
     * a different, newer trade.
     */
    private boolean isCurrentSession(UUID playerUuid, TradeSession session) {
        return getSession(playerUuid) == session && session.getState() == TradeSession.TradeState.TRADING;
    }

    /**
     * Notify other player of confirmation.
     */
    private void notifyConfirmation(TradeSession session, Player confirmer) {
        Player other = Bukkit.getPlayer(session.getOtherPlayer(confirmer.getUniqueId()));
        if (other != null) {
            other.sendMessage(text(i18n("other_confirmed").replace("{PLAYER}", confirmer.getName())));
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
            cancelTrade(session, i18n("cancel_reason_player_offline"));
            return;
        }
        
        double moneyTax = 0;
        int expTax = 0;

        double money1 = session.getPlayerMoney(session.getPlayer1());
        double money2 = session.getPlayerMoney(session.getPlayer2());
        // Read the provider once: a reload may replace it at any time.
        Economy currentEconomy = (Economy) economy;
        boolean moneyAvailable = currentEconomy != null && config.isEnableMoneyTrade();

        // Never move items or experience while silently dropping offered money (UltiKits/UltiTrade#26):
        // money is only withdrawn here, so cancelling leaves every balance untouched and returns items.
        if ((money1 > 0 || money2 > 0) && !moneyAvailable) {
            cancelTrade(session, i18n("cancel_reason_money_unavailable"));
            return;
        }

        // The same rule for experience: an offer is never dropped while the items still move.
        int exp1 = session.getPlayerExp(session.getPlayer1());
        int exp2 = session.getPlayerExp(session.getPlayer2());
        boolean expAvailable = config.isEnableExpTrade();
        if ((exp1 > 0 || exp2 > 0) && !expAvailable) {
            cancelTrade(session, i18n("cancel_reason_exp_unavailable"));
            return;
        }

        // Every check runs before anything moves: a trade cancelled by a check leaves every balance,
        // every experience level and every item where it was (UltiKits/UltiTrade#58).
        double taxRate = config.getTradeTax();
        double moneyTax1 = money1 * taxRate;
        double moneyTax2 = money2 * taxRate;
        if (moneyAvailable) {
            if (money1 > 0 && currentEconomy.getBalance(player1) < money1) {
                cancelTrade(session, i18n("cancel_reason_insufficient_money").replace("{PLAYER}", player1.getName()));
                return;
            }
            if (money2 > 0 && currentEconomy.getBalance(player2) < money2) {
                cancelTrade(session, i18n("cancel_reason_insufficient_money").replace("{PLAYER}", player2.getName()));
                return;
            }
        }
        if (expAvailable) {
            if (exp1 > 0 && getTotalExperience(player1) < exp1) {
                cancelTrade(session, i18n("cancel_reason_insufficient_exp").replace("{PLAYER}", player1.getName()));
                return;
            }
            if (exp2 > 0 && getTotalExperience(player2) < exp2) {
                cancelTrade(session, i18n("cancel_reason_insufficient_exp").replace("{PLAYER}", player2.getName()));
                return;
            }
        }

        // Money is settled before experience or items move (maintainer decision of 2026-10-04,
        // UltiKits/UltiTrade#58). A balance can still change between the check above and the withdrawal
        // (another server sharing the economy's database, an asynchronous plugin), so the economy's own
        // answer decides.
        if (moneyAvailable && !settleMoney(session, currentEconomy, player1, player2, money1, money2, moneyTax1, moneyTax2)) {
            return;
        }
        if (moneyAvailable) {
            moneyTax = moneyTax1 + moneyTax2;
        }

        // Handle experience transfer
        if (expAvailable) {
            double expTaxRate = config.getExpTaxRate();
            int tax1 = (int)(exp1 * expTaxRate);
            int tax2 = (int)(exp2 * expTaxRate);
            expTax = tax1 + tax2;
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
        
        // Close inventories. Dismissed first if a confirmation page is open: this closeInventory()
        // call is what fires it, and unlike closing a TradeGUI, the confirmation page's own close
        // handler cannot otherwise tell this deliberate close apart from an unrelated plugin closing
        // it (UltiKits/UltiTrade#47 review).
        dismissConfirmPageIfOpen(player1);
        dismissConfirmPageIfOpen(player2);
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
     * Move both sides' money, all or nothing (maintainer decision of 2026-10-04, UltiKits/UltiTrade#58).
     * Returns whether it moved; when it did not, the trade has been cancelled -- nothing paid, every
     * payer refunded, no tax taken, every stake returned -- and both players told.
     * <p>
     * Both sides are withdrawn first. A withdrawal the economy refuses, or one that throws, refunds what
     * was already withdrawn and cancels the trade with a reason telling both players the balance changed
     * and nothing was transferred. Only then are the payees paid, each the payer's amount less the tax.
     * A deposit that is refused or throws cancels the trade the same way: every deposit that already
     * landed is taken back from its payee, and every payer is refunded in full. A refund or a take-back
     * that fails is logged at SEVERE, naming both players, their UUIDs, the amount and the currency, so an
     * operator can correct the balances by hand.
     * <p>
     * The economy is passed as {@code Object} and cast where it is used, so no method signature names a
     * Vault type: the class must stay loadable without Vault (UltiKits/UltiTrade#49).
     */
    private boolean settleMoney(TradeSession session, Object economy, Player player1, Player player2,
                                double money1, double money2, double tax1, double tax2) {
        Player[] payers = {player1, player2};
        Player[] payees = {player2, player1};
        double[] amounts = {money1, money2};
        double[] taxes = {tax1, tax2};
        boolean[] withdrawn = new boolean[2];
        for (int i = 0; i < 2; i++) {
            if (amounts[i] <= 0) {
                continue;
            }
            if (!moved(economy, payers[i], amounts[i], true)) {
                refundWithdrawn(economy, payers, payees, amounts, withdrawn);
                cancelTrade(session, i18n("cancel_reason_money_withdraw_refused")
                        .replace("{PLAYER}", payers[i].getName()));
                return false;
            }
            withdrawn[i] = true;
        }
        boolean[] deposited = new boolean[2];
        for (int i = 0; i < 2; i++) {
            if (amounts[i] <= 0) {
                continue;
            }
            if (!moved(economy, payees[i], amounts[i] - taxes[i], false)) {
                for (int j = 0; j < 2; j++) {
                    if (deposited[j] && !moved(economy, payees[j], amounts[j] - taxes[j], true)) {
                        logLostMoney(i18n("log_trade_money_takeback_failed"), economy, payees[j], payers[j], amounts[j] - taxes[j]);
                    }
                }
                refundWithdrawn(economy, payers, payees, amounts, withdrawn);
                cancelTrade(session, i18n("cancel_reason_money_deposit_refused")
                        .replace("{PLAYER}", payees[i].getName()));
                return false;
            }
            deposited[i] = true;
        }
        return true;
    }

    /** Refund every payer whose withdrawal went through; a refund that fails is logged at SEVERE. */
    private void refundWithdrawn(Object economy, Player[] payers, Player[] payees, double[] amounts, boolean[] withdrawn) {
        for (int i = 0; i < 2; i++) {
            if (withdrawn[i] && !moved(economy, payers[i], amounts[i], false)) {
                logLostMoney(i18n("log_trade_money_refund_failed"), economy, payers[i], payees[i], amounts[i]);
            }
        }
    }

    /**
     * One withdrawal ({@code withdraw}) or deposit through the economy. Returns whether the economy
     * reported success; a refused response, a missing one and a thrown exception all count as not moved.
     */
    private boolean moved(Object economy, Player player, double amount, boolean withdraw) {
        try {
            Economy vault = (Economy) economy;
            EconomyResponse response = withdraw ? vault.withdrawPlayer(player, amount) : vault.depositPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (RuntimeException e) {
            String template = withdraw ? i18n("log_trade_money_withdraw_threw") : i18n("log_trade_money_deposit_threw");
            logQuietly(() -> plugin.getLogger().warn(e, template
                    .replace("{PLAYER}", player.getName())
                    .replace("{AMOUNT}", String.valueOf(amount))));
            return false;
        }
    }

    /** The SEVERE line for money a failed trade could not put back: {@code template} says whose and why. */
    private void logLostMoney(String template, Object economy, Player holder, Player other, double amount) {
        String currency;
        try {
            currency = ((Economy) economy).currencyNamePlural();
        } catch (RuntimeException e) {
            currency = "?";
        }
        String line = Placeholders.fill(template,
                "{PLAYER}", holder.getName(),
                "{UUID}", holder.getUniqueId().toString(),
                "{OTHER}", other.getName(),
                "{OTHER_UUID}", other.getUniqueId().toString(),
                "{AMOUNT}", java.math.BigDecimal.valueOf(amount).toPlainString(), // exact: the operator restores this by hand
                "{CURRENCY}", String.valueOf(currency));
        logQuietly(() -> plugin.getLogger().error(line));
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
            // Dismissed first if a confirmation page is open (see completeTrade's own comment on why).
            dismissConfirmPageIfOpen(player1);
            player1.closeInventory();
            String msg = config.getTradeCancelledMessage();
            if (reason != null) {
                msg += " (" + reason + ")";
            }
            player1.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            playFailEffects(player1);
        } else {
            // No inventory to hand the stake to: keep it for the owner's next join, before the
            // session that is its only record is discarded (UltiKits/UltiTrade#32).
            holdStakeForReturn(session.getPlayer1(), session.getPlayerItems(session.getPlayer1()).values());
        }
        
        if (player2 != null) {
            for (ItemStack item : session.getPlayerItems(session.getPlayer2()).values()) {
                if (item != null) {
                    giveOrDrop(player2, item);
                }
            }
            // Dismissed first if a confirmation page is open (see completeTrade's own comment on why).
            dismissConfirmPageIfOpen(player2);
            player2.closeInventory();
            String msg = config.getTradeCancelledMessage();
            if (reason != null) {
                msg += " (" + reason + ")";
            }
            player2.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            playFailEffects(player2);
        } else {
            holdStakeForReturn(session.getPlayer2(), session.getPlayerItems(session.getPlayer2()).values());
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
            cancelTrade(session, i18n("cancel_reason_player_cancelled").replace("{PLAYER}", player.getName()));
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
     * The item is copied before delivery. {@code CraftInventory#addItem} reports its leftover by
     * calling {@code setAmount} on the stack it is given, so passing a caller's own object rewrites it:
     * for a merge into an existing partial stack that left the trade session recording a smaller amount
     * than the player actually staked, and the trade log serialises the session
     * (UltiKits/UltiTrade#37).
     *
     * @param player the player to give the item to, and at whose location any overflow is dropped;
     *               must not be {@code null}
     * @param item   the item to give; {@code null} or an empty stack is nothing to give and is ignored
     * @throws IllegalArgumentException if {@code player} is {@code null}
     * @since 1.0.0
     */
    public void giveOrDrop(Player player, ItemStack item) {
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }
        if (item == null || item.getType().isAir()) {
            return;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    // ==================== Stakes of an owner the server could not find (UltiKits/UltiTrade#32) ====================

    /**
     * Keep the stake of a trade participant the server cannot resolve, so it can be handed back when
     * they next join.
     * <p>
     * A cancelled trade returns each side's stake into that side's inventory; a participant
     * {@code Bukkit#getPlayer} cannot resolve has none, and the session is the stake's only record.
     * The maintainer's decision of 2026-09-24: save it in a pending-returns list and hand it back at
     * the owner's next join through {@link #giveOrDrop}; if the list cannot be saved, drop the items
     * at the owner's last known location and log an error naming the player and the items.
     * <p>
     * Every cancel path reaches this through {@link #cancelTrade(TradeSession, String)}: the
     * completion offline guard, {@link #shutdown()}, and a quit or {@code /trade cancel} whose other
     * side is gone.
     *
     * @param owner the participant whose stake this is
     * @param stake the stacks they staked; {@code null} and empty stacks are ignored
     */
    private void holdStakeForReturn(UUID owner, Collection<ItemStack> stake) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack item : stake) {
            if (item != null && !item.getType().isAir()) {
                stacks.add(item.clone());
            }
        }
        if (stacks.isEmpty()) {
            return;
        }
        String ownerLabel = describeOwner(owner);
        String summary = summarize(stacks);
        RuntimeException saveFailure = null;
        // Only building and inserting the row may lead to the drop below. What runs after a successful
        // insert (the log line) stays outside this block: a failure there must not drop a stake the
        // list already holds, or it would be handed out twice.
        try {
            if (pendingReturns == null) {
                throw new IllegalStateException("the pending-returns list is not available");
            }
            PendingStakeReturn entry = new PendingStakeReturn();
            entry.setOwnerUuid(owner.toString());
            entry.setItems(serializeStacks(stacks));
            entry.setStackCount(stacks.size());
            entry.setCreatedAt(System.currentTimeMillis());
            pendingReturns.insert(entry);
        } catch (RuntimeException e) {
            saveFailure = e;
        }
        if (saveFailure == null) {
            logQuietly(() -> plugin.getLogger().warn(i18n("log_pending_return_saved")
                    .replace("{PLAYER}", ownerLabel)
                    .replace("{COUNT}", String.valueOf(stacks.size()))
                    .replace("{ITEMS}", summary)));
        } else {
            RuntimeException e = saveFailure;
            Location where = lastKnownLocation(owner);
            for (ItemStack item : stacks) {
                where.getWorld().dropItemNaturally(where, item);
            }
            // One pass: a world name is inserted exactly as it is, even when it contains a placeholder
            // token (the class of UltiKits/UltiMail#37).
            logQuietly(() -> plugin.getLogger().error(e, Placeholders.fill(i18n("log_pending_return_save_failed"),
                    "{PLAYER}", ownerLabel,
                    "{COUNT}", String.valueOf(stacks.size()),
                    "{LOCATION}", where.getWorld().getName() + " "
                            + where.getBlockX() + " " + where.getBlockY() + " " + where.getBlockZ(),
                    "{ITEMS}", summary)));
        }
    }

    /**
     * Hand a joining player what fits of every stake saved for them by {@link #holdStakeForReturn}, and
     * keep the rest listed for a later join (maintainer answers of 2026-09-24, amended 2026-09-25 for
     * this step: 「只发装得下的，其余留在列表」).
     * <p>
     * <b>Claim before handing over</b> (maintainer decision of 2026-10-04, UltiKits/UltiTrade#55). Servers
     * sharing one database share the list, but each keeps its own player files, so nothing a server
     * writes into a player's data can tell another server whether a hand-over happened. Each entry is
     * therefore claimed in the list itself before anything from it is handed over: one conditional write
     * ({@code DataOperator#updateIf}) replaces the entry's items with the part that did not fit (nothing,
     * when everything fitted), and applies only while the entry still holds exactly the items this join
     * read. That write is one statement on SQLite and MySQL, so of two servers claiming the same entry
     * only one succeeds; on JSON it runs under the operator's lock. Only the server whose claim succeeded
     * hands the items over; a claim that did not apply (the entry was claimed by another server, or
     * removed, after this join read it) hands nothing over, and a later join reads what is still listed.
     * An entry left with no items is removed.
     * <p>
     * <b>Accepted cost.</b> Once claimed, the items are no longer listed; they reach the player's saved
     * data when the player is saved, which is done straight after. A crash between the claim and that
     * save leaves them undelivered. So that an operator can act on it, each hand-over is logged at WARNING
     * naming the player, the entry and the items before the claim is written; a claim that then does not
     * apply or fails is followed by a line saying that nothing from that entry was handed over. Nothing is dropped at the
     * join, and nothing is ever handed over twice.
     *
     * @param player the player who joined
     */
    public void deliverPendingReturns(Player player) {
        if (pendingReturns == null) {
            return;
        }
        List<PendingStakeReturn> entries;
        try {
            entries = pendingReturns.getAll(WhereCondition.builder()
                    .column("owner_uuid").value(player.getUniqueId().toString()).build());
        } catch (RuntimeException e) {
            plugin.getLogger().error(e, i18n("log_pending_return_lookup_failed")
                    .replace("{PLAYER}", player.getName()));
            return;
        }
        int delivered = 0;
        int kept = 0;
        boolean retry = false;
        for (PendingStakeReturn entry : entries) {
            if (entry.getDeliveryToken() != null && !settleLegacy(player, entry)) {
                retry = true;
                continue;
            }
            if (entry.getItems() == null || entry.getItems().isEmpty()) {
                removeEmpty(player, entry); // claimed whole earlier; only its removal had failed
                continue;
            }
            List<ItemStack> stacks;
            try {
                stacks = deserializeStacks(entry.getItems());
            } catch (RuntimeException e) {
                plugin.getLogger().error(e, i18n("log_pending_return_unreadable")
                        .replace("{PLAYER}", player.getName())
                        .replace("{ID}", String.valueOf(entry.getId())));
                continue;
            }
            int[] outcome = handOver(player, entry, stacks);
            delivered += outcome[0];
            kept += outcome[1];
            retry |= outcome[2] > 0;
        }
        pruneLegacyDeliveries(player, entries);
        if (retry) {
            player.sendMessage(text(i18n("message_pending_return_retry")));
        }
        if (kept > 0) {
            player.sendMessage(text(i18n("message_pending_return_partial")
                    .replace("{COUNT}", String.valueOf(kept))));
            plugin.getLogger().info(i18n("log_pending_return_kept")
                    .replace("{PLAYER}", player.getName())
                    .replace("{COUNT}", String.valueOf(kept)));
        } else if (delivered > 0) {
            player.sendMessage(text(i18n("message_pending_return_delivered")));
        }
        if (delivered > 0) {
            plugin.getLogger().info(i18n("log_pending_return_delivered")
                    .replace("{PLAYER}", player.getName())
                    .replace("{COUNT}", String.valueOf(delivered)));
        }
    }

    /** The persistent-data key in which builds before UltiKits/UltiTrade#55 recorded their hand-overs. */
    private static final NamespacedKey LEGACY_DELIVERIES = NamespacedKey.fromString("ultitrade:pending_return_deliveries");

    /**
     * Settle an entry a build before UltiKits/UltiTrade#55 marked for hand-over, so an upgrade neither
     * hands it over again nor loses it. Such a build marked the entry with a token, gave the items and
     * saved the token into the player's data, and completed the entry only at the next join. If the token
     * is in the player's data, that hand-over reached it: the entry keeps only the part that stayed listed
     * ({@code afterDelivery}); otherwise it never did, and the entry keeps everything. Either way the token
     * is cleared with a conditional write on the items this join read, the same claim as a hand-over, and
     * the entry then goes through the normal hand-over. Returns whether it may; a write that fails or does
     * not apply leaves the entry for a later join.
     * <p>
     * A token is in the data of the server that wrote it only, so an entry marked on one server and
     * settled on another is handed over again there once: the defect UltiKits/UltiTrade#55 removes, left
     * only for entries an earlier build had marked and not yet completed when the server was upgraded.
     */
    private boolean settleLegacy(Player player, PendingStakeReturn entry) {
        String token = entry.getDeliveryToken();
        boolean reachedDisk = legacyDeliveries(player).contains(token);
        PendingStakeReturn settled = copyOf(entry);
        settled.setDeliveryToken(null);
        settled.setAfterDelivery(null);
        boolean won;
        try {
            if (reachedDisk) {
                String after = entry.getAfterDelivery() == null ? "" : entry.getAfterDelivery();
                settled.setItems(after);
                settled.setStackCount(after.isEmpty() ? 0 : deserializeStacks(after).size());
            }
            won = pendingReturns.updateIf(settled, WhereCondition.builder()
                    .column("items").value(entry.getItems()).build());
        } catch (RuntimeException e) {
            plugin.getLogger().error(e, i18n("log_pending_return_mark_failed")
                    .replace("{PLAYER}", player.getName())
                    .replace("{ID}", String.valueOf(entry.getId())));
            return false;
        }
        if (!won) {
            plugin.getLogger().warn(i18n("log_pending_return_claim_lost")
                    .replace("{PLAYER}", player.getName())
                    .replace("{ID}", String.valueOf(entry.getId())));
            return false;
        }
        entry.setItems(settled.getItems());
        entry.setStackCount(settled.getStackCount());
        entry.setDeliveryToken(null);
        entry.setAfterDelivery(null);
        return true;
    }

    /** Drop every legacy marker from the player's data that no entry still carries. */
    private static void pruneLegacyDeliveries(Player player, List<PendingStakeReturn> entries) {
        Set<String> tokens = legacyDeliveries(player);
        if (tokens.isEmpty()) {
            return;
        }
        Set<String> live = new HashSet<>();
        for (PendingStakeReturn entry : entries) {
            if (entry.getDeliveryToken() != null) {
                live.add(entry.getDeliveryToken());
            }
        }
        if (tokens.retainAll(live)) {
            if (tokens.isEmpty()) {
                player.getPersistentDataContainer().remove(LEGACY_DELIVERIES);
            } else {
                player.getPersistentDataContainer().set(LEGACY_DELIVERIES, PersistentDataType.STRING, String.join(",", tokens));
            }
        }
    }

    private static Set<String> legacyDeliveries(Player player) {
        String value = player.getPersistentDataContainer().get(LEGACY_DELIVERIES, PersistentDataType.STRING);
        Set<String> tokens = new LinkedHashSet<>();
        if (value != null && !value.isEmpty()) {
            tokens.addAll(Arrays.asList(value.split(",")));
        }
        return tokens;
    }

    /**
     * Claim one entry and hand over what fits. Returns {items handed over, items kept listed because they
     * did not fit, 1 if the entry could not be claimed and waits for a later join}.
     * <p>
     * The items go into the inventory first, in memory, to learn what fits; nothing can reach the
     * player's saved data before the claim below, as this all runs on the main thread. If the claim does
     * not apply or fails, they are taken back out.
     */
    private int[] handOver(Player player, PendingStakeReturn entry, List<ItemStack> stacks) {
        List<ItemStack> given = new ArrayList<>();
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : stacks) {
            int left = 0;
            for (ItemStack leftover : player.getInventory().addItem(stack.clone()).values()) {
                left += leftover.getAmount();
            }
            int fitted = stack.getAmount() - left;
            if (fitted > 0) {
                ItemStack part = stack.clone();
                part.setAmount(fitted);
                given.add(part);
            }
            if (left > 0) {
                ItemStack part = stack.clone();
                part.setAmount(left);
                remaining.add(part);
            }
        }
        int givenCount = amount(given);
        int keptCount = amount(remaining);
        if (given.isEmpty()) {
            return new int[] {0, keptCount, 0};
        }
        PendingStakeReturn claimed = copyOf(entry);
        claimed.setItems(remaining.isEmpty() ? "" : serializeStacks(remaining));
        claimed.setStackCount(remaining.size());
        // Written before the claim, so it is on record whatever happens after the claim commits: once it
        // has, these items are listed nowhere until the player's data is saved below (the accepted cost
        // of UltiKits/UltiTrade#55). A claim that does not apply is followed by a line saying so.
        String summary = summarize(given);
        logQuietly(() -> plugin.getLogger().warn(Placeholders.fill(i18n("log_pending_return_handing_over"),
                "{PLAYER}", player.getName(),
                "{UUID}", player.getUniqueId().toString(),
                "{ID}", String.valueOf(entry.getId()),
                "{ITEMS}", summary)));
        boolean won;
        try {
            won = pendingReturns.updateIf(claimed, WhereCondition.builder()
                    .column("items").value(entry.getItems()).build());
        } catch (RuntimeException e) {
            player.getInventory().removeItem(given.toArray(new ItemStack[0]));
            logClaimFailure(e, player, entry, summary);
            return new int[] {0, 0, 1};
        }
        if (!won) {
            player.getInventory().removeItem(given.toArray(new ItemStack[0]));
            plugin.getLogger().warn(i18n("log_pending_return_claim_lost")
                    .replace("{PLAYER}", player.getName())
                    .replace("{ID}", String.valueOf(entry.getId())));
            return new int[] {0, 0, 1};
        }
        if (remaining.isEmpty()) {
            removeEmpty(player, claimed);
        }
        try {
            player.saveData();
            // The other half of the operator's record: these items are now in the player's saved data
            // (gate-1 top-up F1). Without it, every successful hand-over would read like one to give back
            // after any later crash.
            logQuietly(() -> plugin.getLogger().info(Placeholders.fill(i18n("log_pending_return_handed_over"),
                    "{ID}", String.valueOf(entry.getId()),
                    "{PLAYER}", player.getName(),
                    "{UUID}", player.getUniqueId().toString(),
                    "{ITEMS}", summary)));
        } catch (RuntimeException e) {
            plugin.getLogger().warn(e, i18n("log_pending_return_player_save_failed")
                    .replace("{COUNT}", String.valueOf(givenCount))
                    .replace("{PLAYER}", player.getName()));
        }
        return new int[] {givenCount, keptCount, 0};
    }

    /**
     * Log a claim whose call threw. The statement may still have been applied (a connection lost after
     * the database committed it), so the row is read again: if it still lists the items this join read,
     * the claim did not apply and the entry is tried again at the next join; otherwise -- the row changed,
     * is gone, or cannot be read -- whether this claim took the items is unknown, and the line names the
     * entry and the items for the operator to check (gate-1 top-up F2). The items have already been taken
     * back out of the inventory, so nothing is ever handed over twice.
     */
    private void logClaimFailure(RuntimeException e, Player player, PendingStakeReturn entry, String items) {
        boolean untouched;
        try {
            PendingStakeReturn now = pendingReturns.getById(entry.getId());
            untouched = now != null && entry.getItems().equals(now.getItems());
        } catch (RuntimeException rereadFailed) {
            untouched = false;
        }
        if (untouched) {
            plugin.getLogger().error(e, i18n("log_pending_return_mark_failed")
                    .replace("{PLAYER}", player.getName())
                    .replace("{ID}", String.valueOf(entry.getId())));
            return;
        }
        plugin.getLogger().error(e, Placeholders.fill(i18n("log_pending_return_claim_unknown"),
                "{ID}", String.valueOf(entry.getId()),
                "{PLAYER}", player.getName(),
                "{UUID}", player.getUniqueId().toString(),
                "{ITEMS}", items));
    }

    /**
     * Remove an entry that lists no items any more. Any server may do this: the entry holds nothing, so
     * removing it cannot take or duplicate an item. A failure leaves it for a later join.
     */
    private void removeEmpty(Player player, PendingStakeReturn entry) {
        try {
            pendingReturns.delById(entry.getId());
        } catch (RuntimeException e) {
            plugin.getLogger().warn(e, i18n("log_pending_return_remove_failed")
                    .replace("{PLAYER}", player.getName())
                    .replace("{ID}", String.valueOf(entry.getId())));
        }
    }

    private static PendingStakeReturn copyOf(PendingStakeReturn entry) {
        PendingStakeReturn copy = new PendingStakeReturn();
        copy.setId(entry.getId());
        copy.setOwnerUuid(entry.getOwnerUuid());
        copy.setItems(entry.getItems());
        copy.setStackCount(entry.getStackCount());
        copy.setCreatedAt(entry.getCreatedAt());
        copy.setDeliveryToken(entry.getDeliveryToken());
        copy.setAfterDelivery(entry.getAfterDelivery());
        return copy;
    }

    private static int amount(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack stack : stacks) {
            n += stack.getAmount();
        }
        return n;
    }

    /**
     * Write a log line after the stake's fate is settled, without letting a logging failure escape:
     * {@link #cancelTrade(TradeSession, String)} must still reach {@code cleanupSession}, or a later
     * cancel of the same session would save or drop the stake a second time.
     */
    private void logQuietly(Runnable line) {
        if (plugin == null) {
            return;
        }
        try {
            line.run();
        } catch (RuntimeException ignored) {
            // The stake is already saved or dropped; the log line is a courtesy.
        }
    }

    /** Where to drop a stake that could not be saved: the owner's last position, else the first world's spawn. */
    private static Location lastKnownLocation(UUID owner) {
        try {
            Location last = Bukkit.getOfflinePlayer(owner).getLocation();
            if (last != null && last.getWorld() != null) {
                return last;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A platform without a stored last position for offline players: use the spawn below.
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    /** The owner's last known name and UUID, for an operator reading the log. */
    private static String describeOwner(UUID owner) {
        String name = null;
        try {
            name = Bukkit.getOfflinePlayer(owner).getName();
        } catch (RuntimeException ignored) {
            // The UUID alone still identifies the player.
        }
        return name == null ? owner.toString() : name + " (" + owner + ")";
    }

    /** "DIAMOND x10, GOLD_INGOT x5": the stacks named with their amounts, for the log. */
    private static String summarize(List<ItemStack> stacks) {
        StringBuilder out = new StringBuilder();
        for (ItemStack item : stacks) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(item.getType().name()).append(" x").append(item.getAmount());
        }
        return out.toString();
    }

    /** Bukkit's own, complete item serialization, as YAML. */
    static String serializeStacks(List<ItemStack> stacks) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("items", stacks);
        return yaml.saveToString();
    }

    /**
     * The inverse of {@link #serializeStacks}.
     *
     * @throws IllegalStateException if the text does not read back as a list of item stacks
     */
    static List<ItemStack> deserializeStacks(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text == null ? "" : text);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException("saved stake is not valid YAML", e);
        }
        List<?> raw = yaml.getList("items");
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("saved stake holds no items");
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Object element : raw) {
            if (!(element instanceof ItemStack) || ((ItemStack) element).getType().isAir()) {
                throw new IllegalStateException("saved stake holds an entry that is not an item");
            }
            stacks.add((ItemStack) element);
        }
        return stacks;
    }

    /**
     * Cleanup session.
     */
    private void cleanupSession(TradeSession session) {
        activeSessions.remove(session.getSessionId());
        playerSessionMap.remove(session.getPlayer1());
        playerSessionMap.remove(session.getPlayer2());
        confirmPageTransitions.remove(session.getPlayer1());
        confirmPageTransitions.remove(session.getPlayer2());
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
                plugin.getLogger().warn(i18n("log_success_particle_missing"));
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
                plugin.getLogger().warn(i18n("log_fail_particle_missing"));
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
