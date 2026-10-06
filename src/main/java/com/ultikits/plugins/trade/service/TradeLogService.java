package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.entity.TradeLogData;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.Service;
import com.ultikits.ultitools.entities.WhereCondition;
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
     * <p>
     * Cached settings are discarded, never written: every change was written when it was made, and a
     * cached copy written back here would revert whatever another server sharing the database changed
     * since this server read it (UltiKits/UltiTrade#54; maintainer decision of 2026-10-06).
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
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
     * Add one completed trade to a player's statistics, as a compare-and-set loop through
     * {@link #change}: the stored counters are read again and the trade added to them, written only if
     * the row still holds what was read. A trade the player completed on another server sharing the
     * database in the meantime is therefore added to, never overwritten (UltiKits/UltiTrade#54). A
     * failure is logged by {@link #change}; the trade itself is already complete.
     */
    private void updatePlayerStats(UUID playerUuid, String playerName,
                                   double moneyTraded, int expTraded) {
        change(playerUuid, playerName, row -> {
            row.incrementTradeStats(moneyTraded, expTraded);
            return true;
        });
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

    /*
     * The settings cache (UltiKits/UltiTrade#54; maintainer decision of 2026-10-06 00:04).
     *
     * settingsCache is a READ cache scoped to a player's time on this server: an entry is added when the
     * player joins (playerJoined) or on the first read while they are online here (cacheIfOnline), holds
     * the row as written after each change (rememberWritten), and is dropped when they quit
     * (playerQuit) and at shutdown. Nothing is ever written from it: every change re-reads the stored row
     * and writes conditionally, so a copy held here can never revert what another server sharing the
     * database changed.
     */

    /**
     * Load a joining player's stored settings into the cache. A player with no stored row is not
     * cached (their first change creates the row). When the stored name differs from the player's
     * current name, the name is written through {@link #change}, so nothing else in the row is touched.
     *
     * @param player the player who joined this server
     */
    public void playerJoined(Player player) {
        UUID playerUuid = player.getUniqueId();
        PlayerTradeSettings row = readStoredRow(playerUuid);
        if (row == null) {
            return;
        }
        if (!player.getName().equals(row.getPlayerName())) {
            ChangeResult renamed = change(playerUuid, player.getName(), unchanged -> false);
            if (renamed.row != null) {
                row = renamed.row;
            }
        }
        settingsCache.put(playerUuid, row);
    }

    /**
     * Drop a player who left this server from the cache. Nothing is written: every change was written
     * when it was made, and the copy held here may be older than what another server has stored since.
     *
     * @param playerUuid the player who quit
     */
    public void playerQuit(UUID playerUuid) {
        settingsCache.remove(playerUuid);
    }

    /**
     * Cache {@code row} for {@code playerUuid} if that player is online on this server and not cached
     * yet (a player who joined before this module was loaded). A player who is not online here -- an
     * offline player's placeholder, for example -- is never cached, so no entry outlives a player's
     * time on this server.
     */
    private void cacheIfOnline(UUID playerUuid, PlayerTradeSettings row) {
        if (row != null && Bukkit.getPlayer(playerUuid) != null) {
            settingsCache.putIfAbsent(playerUuid, row);
        }
    }

    /**
     * After a write, the cache holds the row as written: an existing entry is replaced, and on the main
     * thread an entry is added for a player online here (their first row was created after they joined,
     * so the next change can re-create it with these settings if it is deleted, UltiKits/UltiTrade#57).
     * Off the main thread -- the post-trade statistics -- an entry is only replaced, never added, so a
     * write finishing after the player quit cannot bring their entry back.
     */
    private void rememberWritten(UUID playerUuid, PlayerTradeSettings row) {
        if (Bukkit.isPrimaryThread() && Bukkit.getPlayer(playerUuid) != null) {
            settingsCache.put(playerUuid, row);
        } else {
            settingsCache.computeIfPresent(playerUuid, (uuid, cached) -> row);
        }
    }

    /**
     * Get or create player settings: the cached settings of a player online here, otherwise the stored
     * row, created (with the defaults) when there is none. A stored name that differs from
     * {@code playerName} is updated through {@link #change}.
     *
     * @param playerUuid Player UUID
     * @param playerName Player name
     * @return the player's settings; read-only -- a change goes through this service's change methods
     */
    public PlayerTradeSettings getOrCreateSettings(UUID playerUuid, String playerName) {
        PlayerTradeSettings cached = settingsCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        PlayerTradeSettings settings = readStoredRow(playerUuid);
        if (settings == null) {
            settings = createSettings(playerUuid, playerName);
        } else if (playerName != null && !playerName.equals(settings.getPlayerName())) {
            ChangeResult renamed = change(playerUuid, playerName, unchanged -> false);
            if (renamed.row != null) {
                settings = renamed.row;
            }
        }
        cacheIfOnline(playerUuid, settings);
        return settings;
    }

    /**
     * Get player settings (may return null if not found): the cached settings of a player online here,
     * otherwise the stored row.
     *
     * @param playerUuid Player UUID
     * @return PlayerTradeSettings or null; read-only
     */
    public PlayerTradeSettings getSettings(UUID playerUuid) {
        PlayerTradeSettings cached = settingsCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }
        PlayerTradeSettings settings = readStoredRow(playerUuid);
        cacheIfOnline(playerUuid, settings);
        return settings;
    }

    private PlayerTradeSettings selectCanonicalSettings(List<PlayerTradeSettings> existing) {
        return existing.stream()
            .min(Comparator.comparing(PlayerTradeSettings::getId, Comparator.nullsLast(String::compareTo)))
            .orElse(existing.get(0));
    }
    
    /**
     * Create the player's first settings row, under the id every server derives for this player.
     * <p>
     * A new row's id is the player's UUID, not a random one, so two servers sharing a database that both
     * read "no row" and both create one address the same primary key: the second insert fails instead of
     * adding a second row, and that server takes the row the first one wrote (UltiKits/UltiTrade#57).
     * An insert never overwrites a stored row, so it cannot revert another server's change
     * (UltiKits/UltiTrade#54).
     */
    private PlayerTradeSettings createSettings(UUID playerUuid, String playerName) {
        PlayerTradeSettings settings = new PlayerTradeSettings(playerUuid, playerName);
        settings.setId(playerUuid.toString());
        try {
            settingsOperator.insert(settings);
            return settings;
        } catch (RuntimeException insertFailed) {
            List<PlayerTradeSettings> existing = rowsOf(playerUuid.toString());
            if (existing.isEmpty()) {
                throw insertFailed;
            }
            return selectCanonicalSettings(existing);
        }
    }

    private List<PlayerTradeSettings> rowsOf(String playerUuid) {
        List<PlayerTradeSettings> rows = settingsOperator.query()
            .where("player_uuid").eq(playerUuid)
            .list();
        return rows == null ? new ArrayList<>() : rows;
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
     * @return the trade-enabled state now stored: the toggled state when it was written, otherwise the
     *         state as it is (see {@link #toggle} for whether the write happened)
     */
    public boolean toggleTrade(Player player) {
        SettingsChangeResult result = toggle(player);
        return result.getSettings() != null ? result.getSettings().isTradeEnabled() : isTradeEnabled(player.getUniqueId());
    }

    /**
     * Flip {@code player}'s trading on or off, through {@link #change}: the stored state is read again
     * and flipped, written only if the row still holds what was read. The state flipped is the stored
     * one, so a toggle another server made in the meantime is not reverted -- both toggles count
     * (UltiKits/UltiTrade#54).
     *
     * @param player the player toggling
     * @return what happened, and the settings as now stored ({@code null} unless written)
     */
    public SettingsChangeResult toggle(Player player) {
        ChangeResult result = change(player.getUniqueId(), player.getName(), row -> {
            row.setTradeEnabled(!row.isTradeEnabled());
            return true;
        });
        return new SettingsChangeResult(result.write, result.write == SettingsWrite.WRITTEN ? result.row : null);
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
        return block(player, targetUuid) == SettingsWrite.WRITTEN;
    }

    /**
     * Add {@code targetUuid} to {@code player}'s blocklist, through {@link #change}: the stored row is
     * read again and only the block is added to it, so a setting another server changed is kept.
     *
     * @param player     the player doing the blocking
     * @param targetUuid the player to block
     * @return {@link SettingsWrite#WRITTEN}; {@link SettingsWrite#UNCHANGED} when the stored list already
     *         holds the target; {@link SettingsWrite#BUSY} or {@link SettingsWrite#FAILED} when nothing
     *         could be written
     */
    public SettingsWrite block(Player player, UUID targetUuid) {
        String target = targetUuid.toString();
        return change(player.getUniqueId(), player.getName(), row -> row.blockPlayer(target)).write;
    }

    // ==================== Conditional settings writes (UltiKits/UltiTrade#54) ====================

    /**
     * How many times one settings change is tried before it is given up as contended -- the same bound
     * as UltiEconomy's balance changes (UltiKits/UltiEconomy#41).
     */
    static final int MAX_WRITE_ATTEMPTS = 3;

    /** What one settings change did. */
    public enum SettingsWrite {
        /** The change was written to the stored row. */
        WRITTEN,
        /** The stored row already held what the change asked for; nothing was written. */
        UNCHANGED,
        /**
         * The stored row changed again on every attempt (another server is writing it); nothing was
         * written, and the player is asked to try again.
         */
        BUSY,
        /** The storage failed; nothing was written. */
        FAILED
    }

    /** One change to a player's settings, applied to the row as read. Returns whether it changed anything. */
    @FunctionalInterface
    private interface SettingsChange {
        boolean apply(PlayerTradeSettings row);
    }

    /** The result of {@link #change}: what happened, and the row as now stored ({@code null} when unknown). */
    private static final class ChangeResult {
        final SettingsWrite write;
        final PlayerTradeSettings row;

        ChangeResult(SettingsWrite write, PlayerTradeSettings row) {
            this.write = write;
            this.row = row;
        }
    }

    /** What a settings change did, and the settings as now stored. */
    public static final class SettingsChangeResult {
        private final SettingsWrite write;
        private final PlayerTradeSettings settings;

        /**
         * @param write    what the change did
         * @param settings the settings as now stored when the change was written; otherwise {@code null}
         */
        public SettingsChangeResult(SettingsWrite write, PlayerTradeSettings settings) {
            this.write = write;
            this.settings = settings;
        }

        /** @return what the change did */
        public SettingsWrite getWrite() {
            return write;
        }

        /** @return the settings as now stored when the change was written; otherwise {@code null} */
        public PlayerTradeSettings getSettings() {
            return settings;
        }
    }

    /**
     * Apply one change to a player's stored settings so that it can never revert a change another server
     * sharing the database made (UltiKits/UltiTrade#54; maintainer decision of 2026-10-06, the UltiEconomy
     * pattern).
     * <p>
     * The row is read from the database -- never from the cache -- the change is applied to it, and it is
     * written with {@code DataOperator#updateIf} conditioned on <b>every</b> value it was read with.
     * {@code updateIf} writes every column, so conditioning on all of them is what makes the write change
     * only what this change changed: if any column moved since the read, nothing is written, the row is
     * read again and the change decided again on the new values, at most {@link #MAX_WRITE_ATTEMPTS} times.
     * The player's live name is written with the change when the stored one differs.
     * <p>
     * A player with no stored row gets one, under the player's UUID as id (as {@link #createSettings}):
     * built from the settings this server last held for the player when it holds any -- the row was
     * deleted while the server ran, and the maintainer's decision of 2026-10-04 re-creates it with the
     * current settings (UltiKits/UltiTrade#57) -- and from the defaults otherwise. When another server
     * inserts the same id first, the insert fails and the change is applied to that server's row instead.
     * <p>
     * After a write, the cache holds the row as written ({@link #rememberWritten}).
     * A failure is logged with the player named and nothing is written: a contended row with the reason,
     * a storage error with its exception.
     */
    private ChangeResult change(UUID playerUuid, String playerName, SettingsChange change) {
        String failureLine = i18n("log_settings_save_failed").replace("{PLAYER}", String.valueOf(playerName));
        try {
            for (int attempt = 1; ; attempt++) {
                PlayerTradeSettings row = readStoredRow(playerUuid);
                ChangeResult result = row == null
                        ? createWith(playerUuid, playerName, change)
                        : updateWith(row, playerName, change);
                if (result != null) {
                    if (result.row != null) {
                        rememberWritten(playerUuid, result.row);
                    }
                    return result;
                }
                if (attempt >= MAX_WRITE_ATTEMPTS) {
                    if (plugin != null) {
                        plugin.getLogger().warn(failureLine + ": " + i18n("log_settings_write_contended"));
                    }
                    return new ChangeResult(SettingsWrite.BUSY, null);
                }
            }
        } catch (RuntimeException e) {
            if (plugin != null) {
                plugin.getLogger().warn(e, failureLine);
            }
            return new ChangeResult(SettingsWrite.FAILED, null);
        }
    }

    /** The player's stored row as it is now in the database (the canonical lowest-id row), or {@code null}. */
    private PlayerTradeSettings readStoredRow(UUID playerUuid) {
        List<PlayerTradeSettings> rows = rowsOf(playerUuid.toString());
        return rows.isEmpty() ? null : selectCanonicalSettings(rows);
    }

    /**
     * One attempt on an existing row: apply the change and write it only if the row still holds every
     * value it was read with. Returns {@code null} when it did not (re-read and try again).
     */
    private ChangeResult updateWith(PlayerTradeSettings row, String playerName, SettingsChange change) {
        WhereCondition[] asRead = valuesAsRead(row);
        boolean renamed = playerName != null && !playerName.equals(row.getPlayerName());
        if (renamed) {
            row.setPlayerName(playerName);
        }
        boolean changed = change.apply(row);
        if (!changed && !renamed) {
            return new ChangeResult(SettingsWrite.UNCHANGED, row);
        }
        return settingsOperator.updateIf(row, asRead) ? new ChangeResult(SettingsWrite.WRITTEN, row) : null;
    }

    /**
     * One attempt for a player with no stored row: insert one with the change applied. Returns
     * {@code null} when another server inserted the player's row first (apply the change to that row).
     */
    private ChangeResult createWith(UUID playerUuid, String playerName, SettingsChange change) {
        PlayerTradeSettings held = settingsCache.get(playerUuid);
        PlayerTradeSettings row = held != null ? copyOf(held) : new PlayerTradeSettings(playerUuid, playerName);
        row.setId(playerUuid.toString());
        if (playerName != null) {
            row.setPlayerName(playerName);
        }
        if (!change.apply(row) && held == null) {
            // Nothing to record for a player with no row and no settings this server knows of.
            return new ChangeResult(SettingsWrite.UNCHANGED, null);
        }
        try {
            settingsOperator.insert(row);
        } catch (RuntimeException insertFailed) {
            if (readStoredRow(playerUuid) == null) {
                throw insertFailed;
            }
            return null;
        }
        if (readStoredRow(playerUuid) == null) {
            // The JSON backend ignores an insert whose id it already holds, and returns normally: only
            // reading the row back proves it is stored (UltiKits/UltiTrade#52, #57).
            throw new IllegalStateException("the inserted trade settings row of " + playerUuid + " is not stored");
        }
        if (held != null && plugin != null) {
            plugin.getLogger().warn(i18n("log_settings_row_recreated")
                .replace("{PLAYER}", String.valueOf(row.getPlayerName())));
        }
        return new ChangeResult(SettingsWrite.WRITTEN, row);
    }

    /** A detached copy of {@code settings}, so a row built from it never shares state with the cache. */
    private static PlayerTradeSettings copyOf(PlayerTradeSettings settings) {
        PlayerTradeSettings copy = new PlayerTradeSettings();
        copy.setPlayerUuid(settings.getPlayerUuid());
        copy.setPlayerName(settings.getPlayerName());
        copy.setTradeEnabled(settings.isTradeEnabled());
        copy.setBlockedPlayersJson(settings.getBlockedPlayersJson());
        copy.setTotalTrades(settings.getTotalTrades());
        copy.setTotalMoneyTraded(settings.getTotalMoneyTraded());
        copy.setTotalExpTraded(settings.getTotalExpTraded());
        copy.setLastTradeTime(settings.getLastTradeTime());
        return copy;
    }

    /**
     * One condition per stored column, holding the value {@code row} was read with -- the
     * compare-and-set of {@link #change}. The values are bound exactly as the framework binds them when
     * it writes the row, so a column compares equal to the value it was read as on every backend. A
     * column read as {@code null} cannot be compared ({@code updateIf} refuses a null value, because
     * {@code = NULL} is never true) and is left out; this module never writes a null into any of them.
     */
    private static WhereCondition[] valuesAsRead(PlayerTradeSettings row) {
        List<WhereCondition> conditions = new ArrayList<>();
        addCondition(conditions, "player_uuid", row.getPlayerUuid());
        addCondition(conditions, "player_name", row.getPlayerName());
        addCondition(conditions, "trade_enabled", row.isTradeEnabled());
        addCondition(conditions, "blocked_players", row.getBlockedPlayersJson());
        addCondition(conditions, "total_trades", row.getTotalTrades());
        addCondition(conditions, "total_money_traded", row.getTotalMoneyTraded());
        addCondition(conditions, "total_exp_traded", row.getTotalExpTraded());
        addCondition(conditions, "last_trade_time", row.getLastTradeTime());
        return conditions.toArray(new WhereCondition[0]);
    }

    private static void addCondition(List<WhereCondition> conditions, String column, Object value) {
        if (value != null) {
            conditions.add(WhereCondition.builder().column(column).value(value).build());
        }
    }
    
    /**
     * Unblock a player.
     *
     * @param player Player doing the unblocking
     * @param targetUuid Target to unblock
     * @return true if unblocked successfully
     */
    public boolean unblockPlayer(Player player, UUID targetUuid) {
        return unblock(player, targetUuid) == SettingsWrite.WRITTEN;
    }

    /**
     * Remove {@code targetUuid} from {@code player}'s blocklist, through {@link #change}: the stored list
     * is read again and only that player removed, so a block another server added is kept
     * (UltiKits/UltiTrade#54).
     *
     * @param player     the player doing the unblocking
     * @param targetUuid the player to unblock
     * @return {@link SettingsWrite#WRITTEN}; {@link SettingsWrite#UNCHANGED} when the stored list does not
     *         hold the target; {@link SettingsWrite#BUSY} or {@link SettingsWrite#FAILED} when nothing
     *         could be written
     */
    public SettingsWrite unblock(Player player, UUID targetUuid) {
        String target = targetUuid.toString();
        return change(player.getUniqueId(), player.getName(), row -> row.unblockPlayer(target)).write;
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
