package com.ultikits.plugins.trade;

import com.ultikits.plugins.trade.placeholder.TradePlaceholderExpansion;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

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
        when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.trade.i18n.CatalogueText.answer("zh"));
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
            com.ultikits.plugins.trade.i18n.TradeSeams.speak(tradeService, "zh");
            logService = mock(TradeLogService.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(TradeService.class)).thenReturn(tradeService);
            when(context.getBean(TradeLogService.class)).thenReturn(logService);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.trade.i18n.CatalogueText.answer("zh"));
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

            verify(logger).error(failure, zhLine("log_confirmation_reset_failed"));
        }

        @Test
        @DisplayName("a failing cleanup-task reconciliation is logged at SEVERE and the economy reconciliation still runs")
        void onReloadIsolatesCleanupTaskFailure() {
            RuntimeException failure = new IllegalStateException("scheduler unavailable");
            doThrow(failure).when(logService).reloadCleanupTask();
            doCallRealMethod().when(plugin).onReload();

            assertThatCode(() -> plugin.onReload()).doesNotThrowAnyException();

            verify(tradeService, times(1)).reloadEconomy();
            // The console line follows the language setting (UltiKits/UltiTrade#16)
            verify(logger).error(failure, zhLine("log_cleanup_reconcile_failed"));
        }

        @Test
        @DisplayName("a failing economy reconciliation is logged at SEVERE and does not undo the cleanup-task reconciliation")
        void onReloadIsolatesEconomyFailure() {
            RuntimeException failure = new IllegalStateException("services manager unavailable");
            doThrow(failure).when(tradeService).reloadEconomy();
            doCallRealMethod().when(plugin).onReload();

            assertThatCode(() -> plugin.onReload()).doesNotThrowAnyException();

            verify(logService, times(1)).reloadCleanupTask();
            verify(logger).error(failure, zhLine("log_economy_reconcile_failed"));
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

    /**
     * UltiKits/UltiTrade#17 and #18. {@code RemovedConfigKeysTest} guards the check's predicate; these
     * tests guard its WIRING, which is a separate claim: with the call sites deleted the predicate
     * tests stay green, and a server with leftover keys prints nothing, exactly like a server without
     * them. Both entry points are covered -- module enable and every reload of the module -- because a
     * guard on one would leave the other free to lose its call silently.
     * <p>
     * The operator's file is reached through {@code operatorConfigFile()}, a package-private seam: the
     * framework's {@code getConfigFile} is {@code protected final}, so this package can neither call
     * nor stub it, and a mocked plugin returns {@code null} from it.
     */
    @Nested
    @DisplayName("the removed-key check is actually called (UltiKits/UltiTrade#17, #18)")
    class RemovedKeyCheckWiring {

        private static final String FILE_WITH_REMOVED_KEYS =
                "request-timeout: 30\ntrade-timeout: 120\nmessages:\n  request-timeout: 'x'\n  toggle-on: 'x'\n";

        private static final String FILE_WITHOUT_REMOVED_KEYS =
                "request-timeout: 30\nmessages:\n  request-timeout: 'x'\n";

        private PluginLogger logger;
        private TradeService tradeService;
        private TradeLogService logService;

        private UltiTrade pluginReading(File dir, String body) throws IOException {
            File file = new File(dir, "trade.yml");
            Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));

            UltiTrade plugin = mock(UltiTrade.class);
            logger = mock(PluginLogger.class);
            SimpleContainer context = mock(SimpleContainer.class);
            tradeService = mock(TradeService.class);
            com.ultikits.plugins.trade.i18n.TradeSeams.speak(tradeService, "zh");
            logService = mock(TradeLogService.class);
            when(plugin.getLogger()).thenReturn(logger);
            when(plugin.getContext()).thenReturn(context);
            when(context.getBean(TradeService.class)).thenReturn(tradeService);
            when(context.getBean(TradeLogService.class)).thenReturn(logService);
            when(plugin.i18n(anyString())).thenAnswer(com.ultikits.plugins.trade.i18n.CatalogueText.answer("en"));
            when(plugin.operatorConfigFile()).thenReturn(file);
            return plugin;
        }

        private List<String> warnings() {
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(logger, atLeast(0)).warn(captor.capture());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("POSITIVE CONTROL: enabling the module warns about each leftover key, and still enables")
        void registerSelfWarns(@TempDir File dir) throws IOException {
            UltiTrade plugin = pluginReading(dir, FILE_WITH_REMOVED_KEYS);
            when(plugin.registerSelf()).thenCallRealMethod();

            assertThat(plugin.registerSelf()).isTrue();

            assertThat(warnings()).hasSize(2);
            assertThat(warnings().get(0)).contains("'trade-timeout'");
            assertThat(warnings().get(1)).contains("'messages.toggle-on'");
            verify(tradeService).init();
            verify(logService).init();
        }

        @Test
        @DisplayName("POSITIVE CONTROL: a reload of the module warns about each leftover key, and still reconciles")
        void onReloadWarns(@TempDir File dir) throws IOException {
            UltiTrade plugin = pluginReading(dir, FILE_WITH_REMOVED_KEYS);
            doCallRealMethod().when(plugin).onReload();

            plugin.onReload();

            assertThat(warnings()).hasSize(2);
            assertThat(warnings().get(0)).contains("'trade-timeout'");
            assertThat(warnings().get(1)).contains("'messages.toggle-on'");
            verify(logService).reloadCleanupTask();
            verify(tradeService).reloadEconomy();
        }

        @Test
        @DisplayName("the check reads the same file TradeConfig declares, from one source")
        void readsTheFileTradeConfigDeclares() {
            // Every other test here stubs operatorConfigFile(), so if the path the check resolves
            // ever drifted from the file TradeConfig binds, the production check would read a file
            // that does not exist, return silently, and look exactly like a server with no leftover
            // key. The path the check uses must equal both places TradeConfig names its file.
            UltiTrade plugin = mock(UltiTrade.class);
            when(plugin.operatorConfigPath()).thenCallRealMethod();

            String declared = com.ultikits.plugins.trade.config.TradeConfig.class
                    .getAnnotation(com.ultikits.ultitools.annotations.ConfigEntity.class).value();

            assertThat(declared).isEqualTo("config/trade.yml");
            assertThat(new com.ultikits.plugins.trade.config.TradeConfig().getConfigFilePath())
                    .isEqualTo(declared);
            assertThat(plugin.operatorConfigPath()).isEqualTo(declared);
        }

        @Test
        @DisplayName("a failure inside the check never costs the module its enable or its reload")
        void aFailingCheckNeverFailsEnableOrReload(@TempDir File dir) throws IOException {
            UltiTrade plugin = pluginReading(dir, FILE_WITH_REMOVED_KEYS);
            when(plugin.operatorConfigFile())
                    .thenThrow(new java.io.UncheckedIOException(new IOException("disk unavailable")));
            when(plugin.registerSelf()).thenCallRealMethod();
            doCallRealMethod().when(plugin).onReload();

            assertThat(plugin.registerSelf()).isTrue();
            assertThatCode(plugin::onReload).doesNotThrowAnyException();

            ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
            verify(logger, times(2)).warn(any(Throwable.class), messages.capture());
            assertThat(messages.getAllValues())
                    .allSatisfy(m -> assertThat(m).contains("removed").contains("trade.yml"));
            verify(tradeService).init();
            verify(tradeService).reloadEconomy();
        }

        @Test
        @DisplayName("neither entry point warns when the file holds no removed key")
        void neitherWarnsOnACleanFile(@TempDir File dir) throws IOException {
            // Paired with the two controls above: same entry points, same file, the removed keys
            // taken out and nothing else changed.
            UltiTrade onEnable = pluginReading(dir, FILE_WITHOUT_REMOVED_KEYS);
            when(onEnable.registerSelf()).thenCallRealMethod();
            assertThat(onEnable.registerSelf()).isTrue();
            assertThat(warnings()).isEmpty();

            UltiTrade onReload = pluginReading(dir, FILE_WITHOUT_REMOVED_KEYS);
            doCallRealMethod().when(onReload).onReload();
            onReload.onReload();
            assertThat(warnings()).isEmpty();
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

    /** The Chinese catalogue's console line for {@code key}, or a marker naming the missing key. */
    private static String zhLine(String key) {
        return com.ultikits.plugins.trade.i18n.CatalogueText.entries("zh")
                .getOrDefault(key, "<lang/zh has no " + key + ">");
    }
}
