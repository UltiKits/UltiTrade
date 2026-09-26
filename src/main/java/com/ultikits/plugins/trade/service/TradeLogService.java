package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.entity.TradeLogData;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.interfaces.DataOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing trade logs and player settings.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Service
public class TradeLogService {
    
    @Autowired
    private UltiToolsPlugin plugin;

    @Autowired
    private TradeConfig config;
    
    // Player settings cache
    private final Map<UUID, PlayerTradeSettings> settingsCache = new ConcurrentHashMap<>();

    /**
     * This module's language-file text for {@code key}, in the server's language, so the console lines
     * follow the {@code language} setting (UltiKits/UltiTrade#16). Without an injected plugin the key
     * itself is returned, as the framework renders a missing key: a log write must never fail for want
     * of its failure message.
     */
    private String i18n(String key) {
        return plugin == null ? key : plugin.i18n(key);
    }
    
    // Data operators
    private DataOperator<TradeLogData> logOperator;
    private DataOperator<PlayerTradeSettings> settingsOperator;
    
    // Bukkit plugin instance for scheduler tasks
    private Plugin bukkitPlugin;

    // Cleanup task, and the interval it was scheduled with
    private BukkitTask cleanupTask;
    private int scheduledIntervalHours;
    
    /**
     * Initialize the log service.
     */
    public void init() {
        // Initialize Bukkit plugin reference for scheduler tasks
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");

        // Initialize data operators
        logOperator = plugin.getDataOperator(TradeLogData.class);
        settingsOperator = plugin.getDataOperator(PlayerTradeSettings.class);

        // Start cleanup task
        if (config.isEnableTradeLog()) {
            cleanupTask = scheduleCleanupTask(config.getCleanupIntervalHours());
        }
    }

    /**
     * Reconcile the periodic log cleanup task with the current {@code enable-trade-log} and
     * {@code cleanup-interval-hours} values after a configuration reload (UltiKits/UltiTrade#26).
     * <p>
     * A reload that changes neither key leaves the running task and its countdown untouched.
     * Otherwise the replacement task is scheduled first and only then is the previous one
     * cancelled, so at most one task stays scheduled and a failure to schedule keeps the previous
     * task running. A replacement task first runs one full new interval after the reload.
     */
    public void reloadCleanupTask() {
        boolean enabled = config.isEnableTradeLog();
        int intervalHours = config.getCleanupIntervalHours();
        BukkitTask previous = cleanupTask;
        if (enabled == (previous != null) && (!enabled || intervalHours == scheduledIntervalHours)) {
            return;
        }
        BukkitTask next = enabled ? scheduleCleanupTask(intervalHours) : null;
        if (previous != null) {
            previous.cancel();
        }
        cleanupTask = next;
    }

    private BukkitTask scheduleCleanupTask(int intervalHours) {
        if (bukkitPlugin == null || !bukkitPlugin.isEnabled()) {
            // Nothing can be scheduled through a disabled plugin, and a repeating cleanup has no
            // meaning while the server is stopping (UltiKits/UltiTrade#34).
            plugin.getLogger().warn(i18n("log_cleanup_not_scheduled"));
            scheduledIntervalHours = intervalHours;
            return null;
        }
        long cleanupInterval = intervalHours * 60L * 60L * 20L; // Convert hours to ticks
        BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(
            bukkitPlugin,
            this::cleanupOldLogs,
            cleanupInterval, // Initial delay
            cleanupInterval  // Repeat interval
        );
        scheduledIntervalHours = intervalHours;
        return task;
    }
    
    /**
     * Shutdown the service.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        
        // Save all cached settings
        for (PlayerTradeSettings settings : settingsCache.values()) {
            try {
                settingsOperator.update(settings);
            } catch (Exception e) {
                plugin.getLogger().warn(e,
                    i18n("log_settings_save_failed").replace("{PLAYER}", String.valueOf(settings.getPlayerUuid())));
            }
        }
        settingsCache.clear();
    }
    
    /**
     * Log a completed trade.
     *
     * @param session The completed trade session
     * @param player1 Player 1
     * @param player2 Player 2
     * @param moneyTax Money tax collected
     * @param expTax Experience tax collected
     */
    public void logCompletedTrade(TradeSession session, Player player1, Player player2,
                                   double moneyTax, int expTax) {
        if (!config.isEnableTradeLog()) {
            return;
        }
        
        submitLogWrite(i18n("log_trade_write_failed"), () -> {
            {
                TradeLogData log = new TradeLogData(
                    session.getSessionId(),
                    session.getPlayer1(),
                    player1.getName(),
                    session.getPlayer2(),
                    player2.getName()
                );
                
                // Set items
                log.setPlayer1Items(session.getPlayerItems(session.getPlayer1()).values());
                log.setPlayer2Items(session.getPlayerItems(session.getPlayer2()).values());
                
                // Set money and exp
                log.setPlayer1Money(session.getPlayerMoney(session.getPlayer1()));
                log.setPlayer2Money(session.getPlayerMoney(session.getPlayer2()));
                log.setPlayer1Exp(session.getPlayerExp(session.getPlayer1()));
                log.setPlayer2Exp(session.getPlayerExp(session.getPlayer2()));
                
                // Set tax
                log.setMoneyTaxCollected(moneyTax);
                log.setExpTaxCollected(expTax);
                
                // Mark completed
                log.markCompleted();
                
                // Save to database
                logOperator.insert(log);
                
                // Update player statistics
                updatePlayerStats(session.getPlayer1(), player1.getName(),
                    session.getPlayerMoney(session.getPlayer1()),
                    session.getPlayerExp(session.getPlayer1()));
                updatePlayerStats(session.getPlayer2(), player2.getName(),
                    session.getPlayerMoney(session.getPlayer2()),
                    session.getPlayerExp(session.getPlayer2()));
            }
        });
    }
    
    /**
     * Log a cancelled trade.
     *
     * @param session The cancelled trade session
     * @param reason Cancellation reason
     */
    public void logCancelledTrade(TradeSession session, String reason) {
        if (!config.isEnableTradeLog()) {
            return;
        }
        
        submitLogWrite(i18n("log_cancelled_trade_write_failed"), () -> {
            {
                Player player1 = Bukkit.getPlayer(session.getPlayer1());
                Player player2 = Bukkit.getPlayer(session.getPlayer2());
                
                TradeLogData log = new TradeLogData(
                    session.getSessionId(),
                    session.getPlayer1(),
                    player1 != null ? player1.getName() : "Unknown",
                    session.getPlayer2(),
                    player2 != null ? player2.getName() : "Unknown"
                );
                
                // Set items that were in the trade
                log.setPlayer1Items(session.getPlayerItems(session.getPlayer1()).values());
                log.setPlayer2Items(session.getPlayerItems(session.getPlayer2()).values());
                
                // Set money and exp
                log.setPlayer1Money(session.getPlayerMoney(session.getPlayer1()));
                log.setPlayer2Money(session.getPlayerMoney(session.getPlayer2()));
                log.setPlayer1Exp(session.getPlayerExp(session.getPlayer1()));
                log.setPlayer2Exp(session.getPlayerExp(session.getPlayer2()));
                
                // Mark cancelled
                log.markCancelled(reason);
                
                // Save to database
                logOperator.insert(log);
            }
        });
    }
    
    /**
     * One log write. Declared to allow a checked exception because the framework's
     * {@link com.ultikits.ultitools.interfaces.DataOperator} does, and {@link #runGuarded} is the one
     * place that turns such a failure into a warning.
     *
     * @since 1.0.0
     */
    @FunctionalInterface
    private interface LogWrite {
        void run() throws Exception;
    }

    /**
     * Submit one log write, off the main thread when that is possible and inline when it is not.
     * <p>
     * A log entry is never worth an exception in the caller. {@code CraftScheduler} refuses a task for
     * a plugin that is no longer enabled and throws {@link org.bukkit.plugin.IllegalPluginAccessException},
     * which is the state every caller is in while the server is stopping — and one caller,
     * {@link TradeService#cancelTrade}, has players' staked items to hand back. Writing the record on
     * the calling thread in that case keeps the audit trail complete instead of dropping it, and the
     * write's own failure is contained here rather than reaching the caller (UltiKits/UltiTrade#34).
     *
     * @param failureLine the console line logged if the write fails, in the server's language
     * @param write       the write itself
     * @since 1.0.0
     */
    private void submitLogWrite(String failureLine, LogWrite write) {
        if (bukkitPlugin != null && bukkitPlugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(bukkitPlugin, () -> runGuarded(failureLine, write));
            return;
        }
        runGuarded(failureLine, write);
    }

    /**
     * Run one log write, turning any failure into a warning.
     *
     * @param failureLine the console line logged if the write fails, in the server's language
     * @param write       the write itself
     * @since 1.0.0
     */
    private void runGuarded(String failureLine, LogWrite write) {
        try {
            write.run();
        } catch (Exception e) {
            // Same rule as TradeService#shutdown's rescue: the point of this method is that a log
            // failure reaches nobody, so the reporting itself must not be able to throw.
            if (plugin != null) {
                plugin.getLogger().warn(e, failureLine);
            }
        }
    }

    /**
     * Update player trade statistics.
     */
    private void updatePlayerStats(UUID playerUuid, String playerName, 
                                   double moneyTraded, int expTraded) {
        PlayerTradeSettings settings = getOrCreateSettings(playerUuid, playerName);
        settings.incrementTradeStats(moneyTraded, expTraded);
        saveSettings(settings);
    }
    
    /**
     * Cleanup old logs based on retention days.
     */
    private void cleanupOldLogs() {
        try {
            int retentionDays = config.getLogRetentionDays();
            long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
            
            // Get all logs and filter expired ones
            List<TradeLogData> allLogs = logOperator.getAll();
            int deleted = 0;
            
            for (TradeLogData log : allLogs) {
                if (log.getTradeTime() < cutoffTime) {
                    logOperator.delById(log.getId());
                    deleted++;
                }
            }
            
            if (deleted > 0) {
                plugin.getLogger().info(i18n("log_logs_cleaned")
                    .replace("{COUNT}", String.valueOf(deleted))
                    .replace("{DAYS}", String.valueOf(retentionDays)));
            }
        } catch (Exception e) {
            plugin.getLogger().warn(e, i18n("log_logs_cleanup_failed"));
        }
    }
    
    // ==================== Player Settings Management ====================
    
    /**
     * Get or create player settings.
     *
     * @param playerUuid Player UUID
     * @param playerName Player name
     * @return PlayerTradeSettings instance
     */
    public PlayerTradeSettings getOrCreateSettings(UUID playerUuid, String playerName) {
        // Check cache first
        PlayerTradeSettings cached = settingsCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        
        // Try to load from database
        List<PlayerTradeSettings> existing = settingsOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .list();
        
        PlayerTradeSettings settings;
        if (existing != null && !existing.isEmpty()) {
            settings = selectCanonicalSettings(existing);
            // Update name if changed
            if (!playerName.equals(settings.getPlayerName())) {
                settings.setPlayerName(playerName);
                saveSettings(settings);
            }
        } else {
            // Create new settings
            settings = new PlayerTradeSettings(playerUuid, playerName);
            settingsOperator.insert(settings);
        }
        
        settingsCache.put(playerUuid, settings);
        return settings;
    }
    
    /**
     * Get player settings (may return null if not found).
     *
     * @param playerUuid Player UUID
     * @return PlayerTradeSettings or null
     */
    public PlayerTradeSettings getSettings(UUID playerUuid) {
        PlayerTradeSettings cached = settingsCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        List<PlayerTradeSettings> existing = settingsOperator.query()
            .where("player_uuid").eq(playerUuid.toString())
            .list();
        
        if (existing != null && !existing.isEmpty()) {
            PlayerTradeSettings settings = selectCanonicalSettings(existing);
            settingsCache.put(playerUuid, settings);
            return settings;
        }
        
        return null;
    }

    private PlayerTradeSettings selectCanonicalSettings(List<PlayerTradeSettings> existing) {
        return existing.stream()
            .min(Comparator.comparing(PlayerTradeSettings::getId, Comparator.nullsLast(String::compareTo)))
            .orElse(existing.get(0));
    }
    
    /**
     * Save player settings.
     *
     * @param settings Settings to save
     */
    public void saveSettings(PlayerTradeSettings settings) {
        submitLogWrite(i18n("log_settings_write_failed"), () -> settingsOperator.update(settings));
    }
    
    /**
     * Check if player has trade enabled.
     *
     * @param playerUuid Player UUID
     * @return true if trade is enabled
     */
    public boolean isTradeEnabled(UUID playerUuid) {
        PlayerTradeSettings settings = getSettings(playerUuid);
        return settings == null || settings.isTradeEnabled();
    }
    
    /**
     * Toggle trade status for player.
     *
     * @param player Player
     * @return new trade enabled status
     */
    public boolean toggleTrade(Player player) {
        PlayerTradeSettings settings = getOrCreateSettings(player.getUniqueId(), player.getName());
        settings.setTradeEnabled(!settings.isTradeEnabled());
        saveSettings(settings);
        return settings.isTradeEnabled();
    }
    
    /**
     * Check if target is blocked by player.
     *
     * @param playerUuid Player UUID
     * @param targetUuid Target UUID
     * @return true if target is blocked
     */
    public boolean isBlocked(UUID playerUuid, UUID targetUuid) {
        PlayerTradeSettings settings = getSettings(playerUuid);
        return settings != null && settings.isBlocked(targetUuid.toString());
    }
    
    /**
     * Block a player.
     *
     * @param player Player doing the blocking
     * @param targetUuid Target to block
     * @return true if blocked successfully
     */
    public boolean blockPlayer(Player player, UUID targetUuid) {
        PlayerTradeSettings settings = getOrCreateSettings(player.getUniqueId(), player.getName());
        boolean result = settings.blockPlayer(targetUuid.toString());
        if (result) {
            saveSettings(settings);
        }
        return result;
    }
    
    /**
     * Unblock a player.
     *
     * @param player Player doing the unblocking
     * @param targetUuid Target to unblock
     * @return true if unblocked successfully
     */
    public boolean unblockPlayer(Player player, UUID targetUuid) {
        PlayerTradeSettings settings = getOrCreateSettings(player.getUniqueId(), player.getName());
        boolean result = settings.unblockPlayer(targetUuid.toString());
        if (result) {
            saveSettings(settings);
        }
        return result;
    }
    
    /**
     * Get player statistics.
     *
     * @param playerUuid Player UUID
     * @return PlayerTradeSettings with statistics (may be default values if not found)
     */
    public PlayerTradeSettings getPlayerStats(UUID playerUuid) {
        PlayerTradeSettings settings = getSettings(playerUuid);
        if (settings == null) {
            settings = new PlayerTradeSettings();
            settings.setPlayerUuid(playerUuid.toString());
        }
        return settings;
    }
    
    /**
     * Get trade logs for a player.
     *
     * @param playerUuid Player UUID
     * @param limit Maximum number of logs to return
     * @return List of trade logs
     */
    public List<TradeLogData> getPlayerLogs(UUID playerUuid, int limit) {
        try {
            List<TradeLogData> allLogs = logOperator.getAll();
            String uuidStr = playerUuid.toString();
            
            List<TradeLogData> playerLogs = new ArrayList<>();
            for (TradeLogData log : allLogs) {
                if (uuidStr.equals(log.getPlayer1Uuid()) || uuidStr.equals(log.getPlayer2Uuid())) {
                    playerLogs.add(log);
                }
            }
            
            // Sort by time descending
            playerLogs.sort((a, b) -> Long.compare(b.getTradeTime(), a.getTradeTime()));
            
            // Limit results
            if (playerLogs.size() > limit) {
                return playerLogs.subList(0, limit);
            }
            return playerLogs;
            
        } catch (Exception e) {
            plugin.getLogger().warn(e, i18n("log_player_logs_failed"));
            return new ArrayList<>();
        }
    }
}
