package com.ultikits.plugins.trade;

import com.ultikits.plugins.trade.config.RemovedConfigKeys;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.placeholder.TradePlaceholderExpansion;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;

/**
 * UltiTrade - Player-to-player trading system.
 * <p>
 * Features:
 * - Safe item trading between players
 * - Money trading support (Vault)
 * - Experience trading support
 * - Trade confirmation system
 * - Trade request timeout with BossBar countdown
 * - Trade logging and statistics
 * - Player blacklist
 * - Shift+right-click trading
 * - Large trade confirmation
 * - PlaceholderAPI integration
 * </p>
 *
 * @author wisdomme
 * @version 2.0.0
 */
@UltiToolsModule(scanBasePackages = {"com.ultikits.plugins.trade"})
public class UltiTrade extends UltiToolsPlugin {

    private TradePlaceholderExpansion placeholderExpansion;

    @Override
    public boolean registerSelf() {
        // Deleting a key from TradeConfig does nothing to the operator's existing file, so tell them
        // about any key this version no longer reads (UltiKits/UltiTrade#17, #18).
        warnAboutRemovedConfigKeys();
        blankShippedTextDefaults();

        // Initialize services
        initializeServices();

        // Register PlaceholderAPI expansion if available
        registerPlaceholderAPI();

        getLogger().info(i18n("trade_enabled"));
        return true;
    }

    @Override
    protected void onUnregister() {
        // Shutdown services
        shutdownServices();

        // Unregister PlaceholderAPI expansion
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }

        getLogger().info(i18n("trade_disabled"));
    }

    /**
     * Reconcile the services with configuration values they capture at startup. The framework
     * calls this after it has re-read {@code config/trade.yml}, so both services see the new
     * values (UltiKits/UltiTrade#26). Each reconciliation is isolated: a failure is logged at
     * SEVERE and the other one still runs, because the framework does not catch an exception
     * thrown from this hook.
     * <p>
     * It runs on every reload of this module -- a bare {@code /ul reload} as well as
     * {@code /ul reload UltiTrade} -- and first repeats the removed-key warning, so an operator who
     * edits a key this version no longer reads and reloads is told it has no effect
     * (UltiKits/UltiTrade#17, #18).
     */
    @Override
    protected void onReload() {
        warnAboutRemovedConfigKeys();
        blankShippedTextDefaults();

        TradeLogService logService = getContext().getBean(TradeLogService.class);
        if (logService != null) {
            try {
                logService.reloadCleanupTask();
            } catch (RuntimeException e) {
                getLogger().error(e, i18n("log_cleanup_reconcile_failed"));
            }
        }

        TradeService tradeService = getContext().getBean(TradeService.class);
        if (tradeService != null) {
            try {
                tradeService.reloadEconomy();
            } catch (RuntimeException e) {
                getLogger().error(e, i18n("log_economy_reconcile_failed"));
            }

            // Confirmations given before the reload may cover terms the reload changed, and open
            // windows show those terms: void the first, then redraw the second.
            try {
                tradeService.resetConfirmationsAfterReload();
                tradeService.refreshOpenTradeWindowsAfterReload();
            } catch (RuntimeException e) {
                getLogger().error(e, i18n("log_confirmation_reset_failed"));
            }
        }
    }

    private void warnAboutRemovedConfigKeys() {
        // Advisory only: nothing it throws may cost the module its enable or its reload.
        try {
            RemovedConfigKeys.warnAboutLeftovers(operatorConfigFile(), getLogger()::warn, this);
        } catch (RuntimeException e) {
            getLogger().warn(e, i18n("log_removed_key_check_failed").replace("{FILE}", TradeConfig.CONFIG_FILE));
        }
    }

    /**
     * Blanks the trade-window title and every message in {@code config/trade.yml} that still holds a
     * default an earlier version shipped (all were Chinese) and saves the file, so the language file's
     * text takes over in the server's language; any other value is the operator's and is kept
     * (maintainer ruling 2026-09-24 (d), UltiKits/UltiTrade#16). Runs at start-up and on every reload,
     * after the framework has read the file and before anything reads the texts; a blank value matches
     * no shipped default, so it is never rewritten twice.
     */
    private void blankShippedTextDefaults() {
        TradeConfig config = getConfig(TradeConfig.class);
        if (config == null || !config.migrateLegacyDefaults()) {
            return;
        }
        try {
            config.save();
        } catch (IOException e) {
            getLogger().warn(e, i18n("log_config_default_save_failed").replace("{FILE}", TradeConfig.CONFIG_FILE));
        }
    }

    /**
     * The path of this module's configuration file, relative to its folder -- read from
     * {@link TradeConfig#CONFIG_FILE}, the same constant that entity binds, never a copy of it.
     * Package-private so a test can require the two to be equal.
     *
     * @return {@code config/trade.yml}
     */
    String operatorConfigPath() {
        return TradeConfig.CONFIG_FILE;
    }

    /**
     * The operator's own copy of this module's configuration file.
     * <p>
     * A seam, package-private on purpose. {@code UltiToolsPlugin#getConfigFile} is {@code protected}
     * and {@code final}, so a test in this package can neither call it nor stub it, and a mocked
     * plugin returns {@code null} from it -- which means that without this method the removed-key
     * check's wiring could not be asserted at all, only its predicate.
     *
     * @return the file {@code config/trade.yml} resolves to for this installation
     */
    File operatorConfigFile() {
        return getConfigFile(operatorConfigPath());
    }

    /**
     * Initialize all services required by the plugin.
     * Services are retrieved from the IoC container and initialized in order.
     */
    private void initializeServices() {
        TradeLogService logService = getContext().getBean(TradeLogService.class);
        if (logService != null) {
            logService.init();
        }

        TradeService tradeService = getContext().getBean(TradeService.class);
        if (tradeService != null) {
            tradeService.init();
        }
    }

    /**
     * Shutdown all services in reverse order.
     */
    private void shutdownServices() {
        TradeService tradeService = getContext().getBean(TradeService.class);
        if (tradeService != null) {
            tradeService.shutdown();
        }

        TradeLogService logService = getContext().getBean(TradeLogService.class);
        if (logService != null) {
            logService.shutdown();
        }
    }

    /**
     * Register PlaceholderAPI expansion if the plugin is available.
     */
    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info(i18n("log_placeholderapi_missing"));
            return;
        }

        TradeService tradeService = getContext().getBean(TradeService.class);
        TradeLogService logService = getContext().getBean(TradeLogService.class);

        placeholderExpansion = new TradePlaceholderExpansion(tradeService, logService);
        if (placeholderExpansion.register()) {
            getLogger().info(i18n("log_placeholderapi_registered"));
        }
    }
}
