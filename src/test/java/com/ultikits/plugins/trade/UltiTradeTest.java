package com.ultikits.plugins.trade;

import com.ultikits.plugins.trade.placeholder.TradePlaceholderExpansion;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;
import org.mockito.InOrder;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("UltiTrade Main Class Tests")
class UltiTradeTest {

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @Test
    @DisplayName("registerSelf should return true and log message")
    void registerSelf() throws Exception {
        UltiTrade plugin = mock(UltiTrade.class);
        PluginLogger logger = mock(PluginLogger.class);
        SimpleContainer context = mock(SimpleContainer.class);
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getContext()).thenReturn(context);
        when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(plugin.registerSelf()).thenCallRealMethod();

        boolean result = plugin.registerSelf();

        assertThat(result).isTrue();
        verify(logger).info("UltiTrade 已启用！");
    }

    @Nested
    @DisplayName("Lifecycle hooks (UltiKits/UltiTrade#15)")
    class LifecycleHooks {

        private UltiTrade plugin;
        private PluginLogger logger;
        private TradeService tradeService;
        private TradeLogService logService;

        @BeforeEach
        void setUpPlugin() {
            plugin = mock(UltiTrade.class);
            logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);
            tradeService = mock(TradeService.class);
            logService = mock(TradeLogService.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(TradeService.class)).thenReturn(tradeService);
            when(context.getBean(TradeLogService.class)).thenReturn(logService);
            when(plugin.i18n(anyString())).thenAnswer(inv -> inv.getArgument(0));
            doCallRealMethod().when(plugin).onUnregister();
        }

        @Test
        @DisplayName("onUnregister shuts both services down once, unregisters the expansion once, nulls the field, then logs")
        void onUnregisterShutsDownServicesAndUnregistersExpansion() throws Exception {
            TradePlaceholderExpansion expansion = mock(TradePlaceholderExpansion.class);
            setExpansion(plugin, expansion);

            plugin.onUnregister();

            verify(tradeService, times(1)).shutdown();
            verify(logService, times(1)).shutdown();
            verify(expansion, times(1)).unregister();
            assertThat(getExpansion(plugin)).isNull();
            InOrder order = inOrder(tradeService, logService, expansion, logger);
            order.verify(tradeService).shutdown();
            order.verify(logService).shutdown();
            order.verify(expansion).unregister();
            order.verify(logger).info("UltiTrade 已禁用！");
        }

        @Test
        @DisplayName("onReload reconciles the cleanup task, then the economy provider, once each (UltiKits/UltiTrade#26)")
        void onReloadReconcilesBothServices() {
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            InOrder order = inOrder(logService, tradeService);
            order.verify(logService, times(1)).reloadCleanupTask();
            order.verify(tradeService, times(1)).reloadEconomy();
            order.verify(tradeService, times(1)).resetConfirmationsAfterReload();
            order.verify(tradeService, times(1)).refreshOpenTradeWindowsAfterReload();
            verifyNoMoreInteractions(logService, tradeService);
        }

        @Test
        @DisplayName("a failing economy reconciliation does not skip voiding the confirmations of open trades")
        void onReloadResetsConfirmationsEvenIfEconomyFails() {
            RuntimeException failure = new IllegalStateException("services manager unavailable");
            doThrow(failure).when(tradeService).reloadEconomy();
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            verify(tradeService, times(1)).resetConfirmationsAfterReload();
        }

        @Test
        @DisplayName("a failing confirmation reset is logged at SEVERE and does not propagate")
        void onReloadIsolatesConfirmationResetFailure() {
            RuntimeException failure = new IllegalStateException("session map unavailable");
            doThrow(failure).when(tradeService).resetConfirmationsAfterReload();
            doCallRealMethod().when(plugin).onReload();

            assertThatCode(() -> plugin.onReload()).doesNotThrowAnyException();

            verify(logger).error(failure, UltiTrade.CONFIRMATION_RESET_FAILED);
        }

        @Test
        @DisplayName("a failing cleanup-task reconciliation is logged at SEVERE and the economy reconciliation still runs")
        void onReloadIsolatesCleanupTaskFailure() {
            RuntimeException failure = new IllegalStateException("scheduler unavailable");
            doThrow(failure).when(logService).reloadCleanupTask();
            doCallRealMethod().when(plugin).onReload();

            assertThatCode(() -> plugin.onReload()).doesNotThrowAnyException();

            verify(tradeService, times(1)).reloadEconomy();
            verify(logger).error(failure, "Could not apply enable-trade-log or cleanup-interval-hours from the reloaded configuration;"
                    + " the old-log cleanup task keeps its previous schedule until the next successful /ul reload UltiTrade");
        }

        @Test
        @DisplayName("a failing economy reconciliation is logged at SEVERE and does not undo the cleanup-task reconciliation")
        void onReloadIsolatesEconomyFailure() {
            RuntimeException failure = new IllegalStateException("services manager unavailable");
            doThrow(failure).when(tradeService).reloadEconomy();
            doCallRealMethod().when(plugin).onReload();

            assertThatCode(() -> plugin.onReload()).doesNotThrowAnyException();

            verify(logService, times(1)).reloadCleanupTask();
            verify(logger).error(failure, "Could not apply enable-money-trade from the reloaded configuration;"
                    + " money trading keeps its previous provider until the next successful /ul reload UltiTrade");
        }

        @Test
        @DisplayName("onReload with neither service bean present does nothing and does not throw")
        void onReloadWithoutBeans() {
            when(plugin.getContext().getBean(TradeService.class)).thenReturn(null);
            when(plugin.getContext().getBean(TradeLogService.class)).thenReturn(null);
            doCallRealMethod().when(plugin).onReload();

            assertThatCode(() -> plugin.onReload()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a second onUnregister does not unregister the expansion again")
        void secondOnUnregisterDoesNotDoubleUnregister() throws Exception {
            TradePlaceholderExpansion expansion = mock(TradePlaceholderExpansion.class);
            setExpansion(plugin, expansion);

            plugin.onUnregister();
            plugin.onUnregister();

            verify(expansion, times(1)).unregister();
            assertThat(getExpansion(plugin)).isNull();
        }

        @Test
        @DisplayName("onUnregister with no expansion registered still shuts services down and logs")
        void onUnregisterWithoutExpansion() throws Exception {
            setExpansion(plugin, null);

            assertThatCode(() -> plugin.onUnregister()).doesNotThrowAnyException();

            verify(tradeService, times(1)).shutdown();
            verify(logService, times(1)).shutdown();
            verify(logger).info("UltiTrade 已禁用！");
        }

        @Test
        @DisplayName("the module overrides neither final framework template method and declares the onReload hook (UltiKits/UltiTrade#26)")
        void noTemplateMethodOverrideAndDeclaresReloadHook() {
            assertThatThrownBy(() -> UltiTrade.class.getDeclaredMethod("unregisterSelf"))
                    .isInstanceOf(NoSuchMethodException.class);
            assertThatThrownBy(() -> UltiTrade.class.getDeclaredMethod("reloadSelf"))
                    .isInstanceOf(NoSuchMethodException.class);
            assertThatCode(() -> UltiTrade.class.getDeclaredMethod("onReload"))
                    .doesNotThrowAnyException();
        }
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // reads the private expansion field the hook must null
    private static TradePlaceholderExpansion getExpansion(UltiTrade plugin) throws Exception {
        Field field = UltiTrade.class.getDeclaredField("placeholderExpansion");
        field.setAccessible(true);
        return (TradePlaceholderExpansion) field.get(plugin);
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // seeds the private expansion field registerSelf would set
    private static void setExpansion(UltiTrade plugin, TradePlaceholderExpansion expansion) throws Exception {
        Field field = UltiTrade.class.getDeclaredField("placeholderExpansion");
        field.setAccessible(true);
        field.set(plugin, expansion);
    }
}
