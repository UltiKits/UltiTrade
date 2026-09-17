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
