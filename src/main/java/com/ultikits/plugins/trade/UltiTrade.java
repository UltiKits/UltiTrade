package com.ultikits.plugins.trade;

import com.ultikits.plugins.trade.placeholder.TradePlaceholderExpansion;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

import org.bukkit.Bukkit;

/**
 * UltiTrade - Player-to-player trading system.
 * <p>
 * Features:
 * - Safe item trading between players
 * - Money trading support (Vault)
 * - Experience trading support
 * - Trade confirmation system
 * - Trade timeout with BossBar countdown
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

    static final String CLEANUP_RECONCILE_FAILED =
            "Could not apply enable-trade-log or cleanup-interval-hours from the reloaded configuration;"
            + " the old-log cleanup task keeps its previous schedule until the next successful /ul reload UltiTrade";
    static final String ECONOMY_RECONCILE_FAILED =
            "Could not apply enable-money-trade from the reloaded configuration;"
            + " money trading keeps its previous provider until the next successful /ul reload UltiTrade";

    static final String CONFIRMATION_RESET_FAILED =
            "Could not void the confirmations of open trades or redraw their windows after the reload;"
            + " a trade confirmed or displayed before the reload may complete on the reloaded terms";

    private TradePlaceholderExpansion placeholderExpansion;

    @Override
    public boolean registerSelf() {

        // Initialize services
        initializeServices();

        // Register PlaceholderAPI expansion if available
        registerPlaceholderAPI();

        getLogger().info(i18n("UltiTrade 已启用！"));
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

        getLogger().info(i18n("UltiTrade 已禁用！"));
    }

    /**
     * Reconcile the services with configuration values they capture at startup. The framework
     * calls this after it has re-read {@code config/trade.yml}, so both services see the new
     * values (UltiKits/UltiTrade#26). Each reconciliation is isolated: a failure is logged at
     * SEVERE and the other one still runs, because the framework does not catch an exception
     * thrown from this hook.
     */
    @Override
    protected void onReload() {
        TradeLogService logService = getContext().getBean(TradeLogService.class);
        if (logService != null) {
            try {
                logService.reloadCleanupTask();
            } catch (RuntimeException e) {
                getLogger().error(e, CLEANUP_RECONCILE_FAILED);
            }
        }

        TradeService tradeService = getContext().getBean(TradeService.class);
        if (tradeService != null) {
            try {
                tradeService.reloadEconomy();
            } catch (RuntimeException e) {
                getLogger().error(e, ECONOMY_RECONCILE_FAILED);
            }

            // Confirmations given before the reload may cover terms the reload changed, and open
            // windows show those terms: void the first, then redraw the second.
            try {
                tradeService.resetConfirmationsAfterReload();
                tradeService.refreshOpenTradeWindowsAfterReload();
            } catch (RuntimeException e) {
                getLogger().error(e, CONFIRMATION_RESET_FAILED);
            }
        }
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
            getLogger().info("PlaceholderAPI 未找到，跳过 Placeholder 注册。");
            return;
        }

        TradeService tradeService = getContext().getBean(TradeService.class);
        TradeLogService logService = getContext().getBean(TradeLogService.class);

        placeholderExpansion = new TradePlaceholderExpansion(tradeService, logService);
        if (placeholderExpansion.register()) {
            getLogger().info("PlaceholderAPI 扩展已注册！");
        }
    }
}
