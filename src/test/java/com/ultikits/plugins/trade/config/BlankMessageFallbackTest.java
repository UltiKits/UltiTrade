package com.ultikits.plugins.trade.config;

import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.i18n.TradeSeams;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The trade-window title or a message left blank in {@code config/trade.yml} reads the language file's
 * text, in the server's language; a non-blank value is the operator's and is used as written
 * (maintainer ruling 2026-09-24 (d), UltiKits/UltiTrade#16). Every setting is covered, each through
 * its own getter, which is what every reader of the setting calls.
 */
@DisplayName("A blank text setting reads the language file (UltiKits/UltiTrade#16)")
class BlankMessageFallbackTest {

    /** Each text setting's field, mapped to the language-file key its blank value falls back to. */
    private static final Map<String, String> KEYS = new LinkedHashMap<>();

    static {
        KEYS.put("guiTitle", "gui_title");
        KEYS.put("requestSentMessage", "request_sent");
        KEYS.put("requestReceivedMessage", "request_received");
        KEYS.put("requestTimeoutMessage", "request_timeout");
        KEYS.put("tradeCompleteMessage", "trade_complete");
        KEYS.put("tradeCancelledMessage", "trade_cancelled");
        KEYS.put("tradeDisabledMessage", "trade_disabled_target");
        KEYS.put("playerBlockedMessage", "player_blocked");
    }

    private static TradeConfig bound(String language) {
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        TradeConfig config = new TradeConfig();
        TradeSeams.bind(config, plugin);
        return config;
    }

    private static void set(TradeConfig config, String field, String value) throws Exception {
        Field f = TradeConfig.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(config, value);
    }

    private static String read(TradeConfig config, String field) throws Exception {
        Method getter = TradeConfig.class.getMethod("get" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
        return (String) getter.invoke(config);
    }

    private static String catalogue(String language, String key) {
        return CatalogueText.entries(language).getOrDefault(key, "<lang/" + language + " has no " + key + ">");
    }

    @Test
    @DisplayName("blank reads the English text under language: en, for every setting")
    void blankReadsEnglish() throws Exception {
        TradeConfig config = bound("en");
        for (Map.Entry<String, String> e : KEYS.entrySet()) {
            set(config, e.getKey(), "");
            assertThat(read(config, e.getKey())).as(e.getKey()).isEqualTo(catalogue("en", e.getValue()));
        }
    }

    @Test
    @DisplayName("blank reads the Chinese text under language: zh")
    void blankReadsChinese() throws Exception {
        TradeConfig config = bound("zh");
        for (Map.Entry<String, String> e : KEYS.entrySet()) {
            set(config, e.getKey(), "");
            assertThat(read(config, e.getKey())).as(e.getKey()).isEqualTo(catalogue("zh", e.getValue()));
        }
    }

    @Test
    @DisplayName("whitespace only counts as blank")
    void whitespaceIsBlank() throws Exception {
        TradeConfig config = bound("en");
        set(config, "tradeCompleteMessage", "   ");
        set(config, "guiTitle", "\t");

        assertThat(config.getTradeCompleteMessage()).isEqualTo(catalogue("en", "trade_complete"));
        assertThat(config.getGuiTitle()).isEqualTo(catalogue("en", "gui_title"));
    }

    @Test
    @DisplayName("a customised value is used as written, in any language")
    void customisedIsUsed() throws Exception {
        TradeConfig config = bound("en");
        set(config, "tradeCompleteMessage", "&a成交！");
        set(config, "requestSentMessage", "&aAsked {PLAYER} to trade");

        assertThat(config.getTradeCompleteMessage()).isEqualTo("&a成交！");
        assertThat(config.getRequestSentMessage()).isEqualTo("&aAsked {PLAYER} to trade");
    }
}
