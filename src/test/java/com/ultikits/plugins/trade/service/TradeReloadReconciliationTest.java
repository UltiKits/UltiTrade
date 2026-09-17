package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * UltiKits/UltiTrade#26: {@code /ul reload UltiTrade} re-initialises the same {@link TradeConfig}
 * instance in place and then calls the module's {@code onReload()} hook. Three keys are captured
 * into service state when the services start ({@code enable-money-trade} through the Vault
 * economy lookup, {@code enable-trade-log} and {@code cleanup-interval-hours} through the cleanup
 * task), so the hook must reconcile both services with the reloaded values.
 * <p>
 * Each test edits {@code config/trade.yml}, re-runs {@code TradeConfig#init} on the same instance
 * (what the framework's config reload does), then invokes the module's {@code onReload()} through
 * {@link UltiToolsPlugin}, the way the framework's {@code reloadSelf()} does.
 */
@DisplayName("onReload reconciles init-captured configuration (UltiKits/UltiTrade#26)")
class TradeReloadReconciliationTest {

    @TempDir
    Path moduleFolder;

    private File configFile;
    private UltiTrade plugin;
    private TradeConfig config;
    private TradeService tradeService;
    private TradeLogService logService;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        configFile = moduleFolder.resolve("config").resolve("trade.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();

        plugin = mock(UltiTrade.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());
        config = new TradeConfig();

        tradeService = new TradeService();
        UltiTradeTestHelper.setField(tradeService, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(tradeService, "config", config);

        logService = new TradeLogService();
        UltiTradeTestHelper.setField(logService, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(logService, "config", config);

        SimpleContainer context = mock(SimpleContainer.class);
        when(context.getBean(TradeService.class)).thenReturn(tradeService);
        when(context.getBean(TradeLogService.class)).thenReturn(logService);
        doReturn(context).when(plugin).getContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @Nested
    @DisplayName("enable-money-trade")
    class MoneyTrade {

        private Economy vaultEconomy;
        private Server server;

        @BeforeEach
        void registerVaultProvider() {
            server = Bukkit.getServer();
            vaultEconomy = mock(Economy.class);
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
            when(registration.getProvider()).thenReturn(vaultEconomy);
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(mock(Plugin.class));
            when(server.getServicesManager().getRegistration(Economy.class)).thenReturn(registration);
        }

        @Test
        @DisplayName("false at startup, then true and reload: money trading becomes available without a restart")
        void falseToTrueEnablesMoneyTrade() throws Exception {
            loadConfig("enable-money-trade: false\n");
            tradeService.init();
            // Precondition is verifiably false at boot: no lookup happened and money trade is off.
            verify(server.getServicesManager(), never()).getRegistration(any());
            assertThat(tradeService.hasEconomy()).isFalse();

            reload("enable-money-trade: true\n");

            assertThat(tradeService.hasEconomy()).isTrue();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);
        }

        @Test
        @DisplayName("true at startup, then false and reload: money trading stops and the provider is dropped")
        void trueToFalseDisablesMoneyTrade() throws Exception {
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.hasEconomy()).isTrue();

            reload("enable-money-trade: false\n");

            assertThat(tradeService.hasEconomy()).isFalse();
            assertThat(tradeService.getEconomy()).isNull();
        }

        @Test
        @DisplayName("false, true, false, true across reloads ends with the current provider and registers nothing")
        void repeatedTogglesStayConsistent() throws Exception {
            loadConfig("enable-money-trade: false\n");
            tradeService.init();

            reload("enable-money-trade: true\n");
            reload("enable-money-trade: false\n");
            assertThat(tradeService.hasEconomy()).isFalse();
            reload("enable-money-trade: true\n");

            assertThat(tradeService.hasEconomy()).isTrue();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);
            verify(server.getServicesManager(), never()).register(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("enable-trade-log and cleanup-interval-hours")
    class CleanupTask {

        private BukkitScheduler scheduler;
        private BukkitTask first;
        private BukkitTask second;
        private BukkitTask third;

        @BeforeEach
        void distinctTasks() {
            scheduler = Bukkit.getServer().getScheduler();
            first = mock(BukkitTask.class);
            second = mock(BukkitTask.class);
            third = mock(BukkitTask.class);
            when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                    .thenReturn(first, second, third);
        }

        @Test
        @DisplayName("true at startup, then false and reload: the running cleanup task is cancelled and none is started")
        void trueToFalseCancelsCleanupTask() throws Exception {
            loadConfig("enable-trade-log: true\n");
            logService.init();
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());

            reload("enable-trade-log: false\n");

            verify(first, times(1)).cancel();
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isNull();
        }

        @Test
        @DisplayName("false at startup, then true and reload: exactly one cleanup task is started with the configured interval")
        void falseToTrueStartsCleanupTask() throws Exception {
            loadConfig("enable-trade-log: false\ncleanup-interval-hours: 6\n");
            logService.init();
            verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());

            reload("enable-trade-log: true\ncleanup-interval-hours: 6\n");

            long ticks = 6L * 60L * 60L * 20L;
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), eq(ticks), eq(ticks));
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(first);
        }

        @Test
        @DisplayName("changing cleanup-interval-hours and reloading twice replaces the task each time without accumulating tasks")
        void intervalChangeReschedulesWithoutAccumulating() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\n");
            logService.init();

            reload("enable-trade-log: true\ncleanup-interval-hours: 12\n");
            reload("enable-trade-log: true\ncleanup-interval-hours: 12\n");

            long twelveHours = 12L * 60L * 60L * 20L;
            verify(scheduler, times(2)).runTaskTimerAsynchronously(
                    any(), any(Runnable.class), eq(twelveHours), eq(twelveHours));
            verify(first, times(1)).cancel();
            verify(second, times(1)).cancel();
            verify(third, never()).cancel();
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(third);
        }
    }

    private void loadConfig(String yaml) throws Exception {
        Files.write(configFile.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
        config.init(plugin);
    }

    /** Edit the file, re-initialise the same config instance, then run the module's reload hook. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // onReload() is protected on the framework base class
    private void reload(String yaml) throws Exception {
        loadConfig(yaml);
        Method hook = UltiToolsPlugin.class.getDeclaredMethod("onReload");
        hook.setAccessible(true);
        hook.invoke(plugin);
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
