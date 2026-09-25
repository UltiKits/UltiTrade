package com.ultikits.plugins.trade;

import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An operator file still holding a text default an earlier version shipped (the trade-window title and
 * seven messages were Chinese) is rewritten to blank and saved, at start-up and on reload, so the
 * language file's text takes over in the server's language; any other value is the operator's and is
 * kept (maintainer ruling 2026-09-24 (d), UltiKits/UltiTrade#16).
 * <p>
 * The shipped values are copied from this module's history (every revision of {@code TradeConfig}):
 * each setting had exactly one default until this change.
 */
@DisplayName("Shipped Chinese text defaults give way to the language file (UltiKits/UltiTrade#16)")
class LegacyMessageDefaultsTest {

    private static final Map<String, String> SHIPPED = new LinkedHashMap<>();

    static {
        SHIPPED.put("guiTitle", "&6与 {PLAYER} 交易");
        SHIPPED.put("requestSentMessage", "&a已向 &f{PLAYER} &a发送交易请求！");
        SHIPPED.put("requestReceivedMessage", "&e{PLAYER} &f请求与你交易！输入 /trade accept 接受");
        SHIPPED.put("requestTimeoutMessage", "&c交易请求已超时！");
        SHIPPED.put("tradeCompleteMessage", "&a交易完成！");
        SHIPPED.put("tradeCancelledMessage", "&c交易已取消！");
        SHIPPED.put("tradeDisabledMessage", "&c对方已关闭交易功能！");
        SHIPPED.put("playerBlockedMessage", "&c对方已将你加入黑名单！");
    }

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    private static void set(TradeConfig config, String field, String value) throws Exception {
        Field f = TradeConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(config, value);
    }

    private static String get(TradeConfig config, String field) throws Exception {
        Field f = TradeConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        return (String) f.get(config);
    }

    private TradeConfig shipped() throws Exception {
        TradeConfig config = spy(new TradeConfig());
        doNothing().when(config).save();
        for (Map.Entry<String, String> e : SHIPPED.entrySet()) {
            set(config, e.getKey(), e.getValue());
        }
        return config;
    }

    private UltiTrade pluginWith(TradeConfig config, String language) {
        UltiTrade plugin = mock(UltiTrade.class);
        SimpleContainer context = mock(SimpleContainer.class);
        when(context.getBean(TradeService.class)).thenReturn(mock(TradeService.class));
        when(context.getBean(TradeLogService.class)).thenReturn(mock(TradeLogService.class));
        when(plugin.getContext()).thenReturn(context);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        when(plugin.getLogger()).thenReturn(mock(PluginLogger.class));
        when(plugin.getConfig(TradeConfig.class)).thenReturn(config);
        return plugin;
    }

    private void assertAllBlank(TradeConfig config) throws Exception {
        for (String field : SHIPPED.keySet()) {
            assertThat(get(config, field)).as(field).isEmpty();
        }
    }

    @Test
    @DisplayName("start-up blanks every shipped default and saves the file")
    void startUp() throws Exception {
        TradeConfig config = shipped();
        UltiTrade plugin = pluginWith(config, "en");
        when(plugin.registerSelf()).thenCallRealMethod();

        plugin.registerSelf();

        assertAllBlank(config);
        verify(config).save();
    }

    @Test
    @DisplayName("/ul reload blanks every shipped default and saves the file")
    void reload() throws Exception {
        TradeConfig config = shipped();
        UltiTrade plugin = pluginWith(config, "en");
        doCallRealMethod().when(plugin).onReload();

        plugin.onReload();

        assertAllBlank(config);
        verify(config).save();
    }

    @Test
    @DisplayName("a customised value is kept while the shipped ones beside it are blanked")
    void customisedIsKept() throws Exception {
        TradeConfig config = shipped();
        set(config, "tradeCompleteMessage", "&aDeal done!");
        set(config, "guiTitle", SHIPPED.get("guiTitle") + " ");
        UltiTrade plugin = pluginWith(config, "en");
        when(plugin.registerSelf()).thenCallRealMethod();

        plugin.registerSelf();

        assertThat(get(config, "tradeCompleteMessage")).isEqualTo("&aDeal done!");
        assertThat(get(config, "guiTitle")).isEqualTo(SHIPPED.get("guiTitle") + " ");
        assertThat(Arrays.asList(get(config, "requestSentMessage"), get(config, "playerBlockedMessage")))
                .containsOnly("");
        verify(config).save();
    }

    @Test
    @DisplayName("a file already blank is not rewritten on the next start")
    void blankIsNotRewritten() throws Exception {
        TradeConfig config = shipped();
        for (String field : SHIPPED.keySet()) {
            set(config, field, "");
        }
        UltiTrade plugin = pluginWith(config, "en");
        when(plugin.registerSelf()).thenCallRealMethod();

        plugin.registerSelf();

        verify(config, never()).save();
    }

    @Test
    @DisplayName("a failed save is reported in the configured language and costs nothing else")
    void saveFailureReported() throws Exception {
        TradeConfig config = shipped();
        doThrow(new IOException("read-only")).when(config).save();
        UltiTrade plugin = pluginWith(config, "zh");
        when(plugin.registerSelf()).thenCallRealMethod();

        assertThat(plugin.registerSelf()).isTrue();

        String expected = CatalogueText.entries("zh").getOrDefault("log_config_default_save_failed",
                "<lang/zh has no log_config_default_save_failed>").replace("{FILE}", TradeConfig.CONFIG_FILE);
        verify(plugin.getLogger()).warn(any(IOException.class), eq(expected));
    }
}
