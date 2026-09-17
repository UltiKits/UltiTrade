package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(mock(Plugin.class));
            stubProvider(server, vaultEconomy);
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
        @DisplayName("false, true, false, true across reloads ends with the provider registered at the last lookup")
        void repeatedTogglesStayConsistent() throws Exception {
            Economy secondProvider = mock(Economy.class);
            loadConfig("enable-money-trade: false\n");
            tradeService.init();

            reload("enable-money-trade: true\n");
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);
            reload("enable-money-trade: false\n");
            assertThat(tradeService.hasEconomy()).isFalse();
            stubProvider(server, secondProvider);
            reload("enable-money-trade: true\n");

            assertThat(tradeService.hasEconomy()).isTrue();
            assertThat(tradeService.getEconomy()).isSameAs(secondProvider);
        }

        @Test
        @DisplayName("true stays true while the provider is replaced: the reload adopts the new provider")
        void unchangedKeyAdoptsReplacedProvider() throws Exception {
            Economy replacement = mock(Economy.class);
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);

            stubProvider(server, replacement);
            reload("enable-money-trade: true\n");

            assertThat(tradeService.getEconomy()).isSameAs(replacement);
        }

        @Test
        @DisplayName("provider present, then absent, then a different one: each reload holds exactly what the lookup found")
        void providerPresentAbsentDifferent() throws Exception {
            Economy different = mock(Economy.class);
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);

            when(server.getServicesManager().getRegistration(Economy.class)).thenReturn(null);
            reload("enable-money-trade: true\n");
            assertThat(tradeService.getEconomy()).isNull();
            assertThat(tradeService.hasEconomy()).isFalse();

            stubProvider(server, different);
            reload("enable-money-trade: true\n");
            assertThat(tradeService.getEconomy()).isSameAs(different);
            assertThat(tradeService.hasEconomy()).isTrue();
        }

        @Test
        @DisplayName("Vault removed while money trading stays on: the held provider is dropped and the warning is logged")
        void vaultGoneDropsProvider() throws Exception {
            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);

            when(server.getPluginManager().getPlugin("Vault")).thenReturn(null);
            reload("enable-money-trade: true\n");

            assertThat(tradeService.getEconomy()).isNull();
            verify(UltiTradeTestHelper.getMockLogger()).warn("Vault not found! Money trading disabled.");
        }

        @Test
        @DisplayName("money trading on and Vault present without a provider: the reload logs that money trading is disabled")
        void noProviderIsReported() throws Exception {
            when(server.getServicesManager().getRegistration(Economy.class)).thenReturn(null);
            loadConfig("enable-money-trade: true\n");
            tradeService.init();

            reload("enable-money-trade: true\n");

            verify(UltiTradeTestHelper.getMockLogger(), times(2))
                    .warn("No Vault economy provider is registered! Money trading disabled.");
        }

        @Test
        @DisplayName("money trading off: a reload performs no lookup and logs nothing, whether or not Vault exists")
        void moneyTradeOffLogsNothing() throws Exception {
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(null);
            loadConfig("enable-money-trade: false\n");
            tradeService.init();

            reload("enable-money-trade: false\n");

            verify(server.getServicesManager(), never()).getRegistration(any());
            verify(UltiTradeTestHelper.getMockLogger(), never()).warn(anyString());
        }
    }

    @Nested
    @DisplayName("a trade that is open while a reload turns money trading off")
    class MidTradeDisable {

        private Economy vaultEconomy;
        private Server server;
        private TradeLogService sessionLog;
        private Player offerer;
        private Player counterparty;
        private PlayerInventory offererInventory;
        private PlayerInventory counterpartyInventory;
        private ItemStack offererItem;
        private ItemStack counterpartyItem;
        private TradeSession session;

        @BeforeEach
        void openTradeWithMoneyOffered() throws Exception {
            server = Bukkit.getServer();
            vaultEconomy = UltiTradeTestHelper.createMockEconomy();
            when(server.getPluginManager().getPlugin("Vault")).thenReturn(mock(Plugin.class));
            stubProvider(server, vaultEconomy);

            sessionLog = mock(TradeLogService.class);
            UltiTradeTestHelper.setField(tradeService, "logService", sessionLog);

            offerer = UltiTradeTestHelper.createMockPlayer("Offerer", UUID.randomUUID());
            counterparty = UltiTradeTestHelper.createMockPlayer("Counterparty", UUID.randomUUID());
            UUID offererId = offerer.getUniqueId();
            UUID counterpartyId = counterparty.getUniqueId();
            doReturn(offerer).when(server).getPlayer(offererId);
            doReturn(counterparty).when(server).getPlayer(counterpartyId);
            offererInventory = offerer.getInventory();
            counterpartyInventory = counterparty.getInventory();
            when(offererInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
            when(counterpartyInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

            loadConfig("enable-money-trade: true\n");
            tradeService.init();
            assertThat(tradeService.hasEconomy()).isTrue();

            session = new TradeSession(offerer, counterparty);
            offererItem = mock(ItemStack.class);
            counterpartyItem = mock(ItemStack.class);
            session.setItem(offerer.getUniqueId(), 0, offererItem);
            session.setItem(counterparty.getUniqueId(), 0, counterpartyItem);
            session.setMoney(offerer.getUniqueId(), 1000.0);
            Map<UUID, TradeSession> activeSessions = UltiTradeTestHelper.getField(tradeService, "activeSessions");
            Map<UUID, UUID> playerSessionMap = UltiTradeTestHelper.getField(tradeService, "playerSessionMap");
            activeSessions.put(session.getSessionId(), session);
            playerSessionMap.put(offerer.getUniqueId(), session.getSessionId());
            playerSessionMap.put(counterparty.getUniqueId(), session.getSessionId());
        }

        @Test
        @DisplayName("A offers money, a reload turns money trading off, B confirms: the trade is cancelled, nothing moves, no completed trade is logged")
        void confirmAfterDisableCancelsTrade() throws Exception {
            session.setConfirmed(offerer.getUniqueId(), true);

            reload("enable-money-trade: false\n");
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
            // Balances unchanged: nothing was withdrawn or deposited.
            verify(vaultEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(vaultEconomy, never()).depositPlayer(any(Player.class), anyDouble());
            // No item crossed sides; each side got back exactly its own offer.
            verify(counterpartyInventory, never()).addItem(offererItem);
            verify(offererInventory, never()).addItem(counterpartyItem);
            verify(offererInventory, times(1)).addItem(offererItem);
            verify(counterpartyInventory, times(1)).addItem(counterpartyItem);
            // No completed-trade log entry and therefore no money statistic.
            verify(sessionLog, never()).logCompletedTrade(any(), any(), any(), anyDouble(), anyInt());
            // Both players are told why.
            verify(offerer).sendMessage(contains(MONEY_UNAVAILABLE_REASON));
            verify(counterparty).sendMessage(contains(MONEY_UNAVAILABLE_REASON));
            assertThat(tradeService.isTrading(offerer.getUniqueId())).isFalse();
        }

        @Test
        @DisplayName("the accepting player offered the money: a reload that turns money trading off still cancels the trade")
        void counterpartyMoneyAfterDisableCancelsTrade() throws Exception {
            session.setMoney(offerer.getUniqueId(), 0.0);
            session.setMoney(counterparty.getUniqueId(), 1000.0);
            session.setConfirmed(offerer.getUniqueId(), true);

            reload("enable-money-trade: false\n");
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
            verify(vaultEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(vaultEconomy, never()).depositPlayer(any(Player.class), anyDouble());
            verify(counterpartyInventory, never()).addItem(offererItem);
            verify(offererInventory, never()).addItem(counterpartyItem);
            verify(sessionLog, never()).logCompletedTrade(any(), any(), any(), anyDouble(), anyInt());
            verify(counterparty).sendMessage(contains(MONEY_UNAVAILABLE_REASON));
        }

        @Test
        @DisplayName("provider still held but enable-money-trade already false in memory (hook not reached): the money trade is cancelled")
        void heldProviderWithMoneyTradeOffCancelsTrade() throws Exception {
            session.setConfirmed(offerer.getUniqueId(), true);

            // Only the configuration changes: onReload() is not run, so the provider stays held.
            loadConfig("enable-money-trade: false\n");
            assertThat(tradeService.getEconomy()).isSameAs(vaultEconomy);
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.CANCELLED);
            verify(vaultEconomy, never()).withdrawPlayer(any(Player.class), anyDouble());
            verify(vaultEconomy, never()).depositPlayer(any(Player.class), anyDouble());
        }

        @Test
        @DisplayName("no money offered: a reload that turns money trading off does not stop an item-only trade")
        void itemOnlyTradeStillCompletes() throws Exception {
            session.setMoney(offerer.getUniqueId(), 0.0);
            session.setConfirmed(offerer.getUniqueId(), true);

            reload("enable-money-trade: false\n");
            tradeService.confirmTrade(counterparty);

            assertThat(session.getState()).isEqualTo(TradeSession.TradeState.COMPLETED);
            verify(counterpartyInventory).addItem(offererItem);
            verify(offererInventory).addItem(counterpartyItem);
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
        @DisplayName("changing cleanup-interval-hours replaces the task once; a second reload with the same interval keeps it")
        void intervalChangeReschedulesWithoutAccumulating() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\n");
            logService.init();

            reload("enable-trade-log: true\ncleanup-interval-hours: 12\n");
            reload("enable-trade-log: true\ncleanup-interval-hours: 12\n");

            long twelveHours = 12L * 60L * 60L * 20L;
            verify(scheduler, times(1)).runTaskTimerAsynchronously(
                    any(), any(Runnable.class), eq(twelveHours), eq(twelveHours));
            verify(scheduler, times(2)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
            verify(first, times(1)).cancel();
            verify(second, never()).cancel();
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(second);
        }

        @Test
        @DisplayName("a reload that changes neither cleanup key keeps the running task and its countdown")
        void unrelatedReloadKeepsRunningTask() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\nmax-distance: 50\n");
            logService.init();

            reload("enable-trade-log: true\ncleanup-interval-hours: 24\nmax-distance: 10\n");

            verify(first, never()).cancel();
            verify(scheduler, times(1)).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(first);
        }

        @Test
        @DisplayName("logging on, off, on again with the same interval: a cleanup task is scheduled again")
        void onOffOnSameIntervalRestartsTask() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\n");
            logService.init();

            reload("enable-trade-log: false\ncleanup-interval-hours: 24\n");
            verify(first, times(1)).cancel();
            reload("enable-trade-log: true\ncleanup-interval-hours: 24\n");

            long day = 24L * 60L * 60L * 20L;
            verify(scheduler, times(2)).runTaskTimerAsynchronously(any(), any(Runnable.class), eq(day), eq(day));
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(second);
        }

        @Test
        @DisplayName("logging off at startup and still off after a reload: nothing is scheduled")
        void offStaysOffSchedulesNothing() throws Exception {
            loadConfig("enable-trade-log: false\n");
            logService.init();

            reload("enable-trade-log: false\ncleanup-interval-hours: 6\n");

            verify(scheduler, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
        }

        @Test
        @DisplayName("if scheduling the replacement task fails, the previous task keeps running")
        void failedRescheduleKeepsPreviousTask() throws Exception {
            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 24\n");
            logService.init();
            when(scheduler.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong()))
                    .thenThrow(new IllegalStateException("scheduler unavailable"));

            loadConfig("enable-trade-log: true\ncleanup-interval-hours: 12\n");
            assertThatThrownBy(() -> logService.reloadCleanupTask()).isInstanceOf(IllegalStateException.class);

            verify(first, never()).cancel();
            assertThat((BukkitTask) UltiTradeTestHelper.getField(logService, "cleanupTask")).isSameAs(first);
        }
    }

    private static final String MONEY_UNAVAILABLE_REASON = "\u91D1\u5E01\u4EA4\u6613\u5F53\u524D\u4E0D\u53EF\u7528"; // "金币交易当前不可用"

    /** Make the services manager return a registration for {@code provider}, built before the stub starts. */
    private static void stubProvider(Server server, Economy provider) {
        ServicesManager servicesManager = server.getServicesManager();
        RegisteredServiceProvider<Economy> registration = registrationOf(provider);
        doReturn(registration).when(servicesManager).getRegistration(Economy.class);
    }

    private static RegisteredServiceProvider<Economy> registrationOf(Economy provider) {
        @SuppressWarnings("unchecked")
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(provider);
        return registration;
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
