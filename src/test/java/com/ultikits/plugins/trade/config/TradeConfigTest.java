package com.ultikits.plugins.trade.config;

import com.ultikits.ultitools.annotations.ConfigEntry;

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TradeConfig Tests")
class TradeConfigTest {

    private TradeConfig config;

    @BeforeEach
    void setUp() {
        config = new TradeConfig();
        // Bound to a plugin answering from the Chinese language file, as the framework's init() binds
        // it: a blank text setting reads its text from there (UltiKits/UltiTrade#16).
        com.ultikits.ultitools.abstracts.UltiToolsPlugin plugin =
                org.mockito.Mockito.mock(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class);
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(com.ultikits.plugins.trade.i18n.CatalogueText.answer("zh"));
        com.ultikits.plugins.trade.i18n.TradeSeams.bind(config, plugin);
    }


    /** Every key TradeConfig declares, read from its {@code @ConfigEntry} annotations. */
    private static List<String> declaredKeys() {
        List<String> keys = new ArrayList<>();
        for (Field field : TradeConfig.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry != null) {
                keys.add(entry.path());
            }
        }
        return keys;
    }

    // The two tests below are top-level methods, not @Nested, so that each can be selected on its
    // own by name (-Dtest=TradeConfigTest#method) for its issue's revert proof.

    /**
     * UltiKits/UltiTrade#18. A pending trade request expires after {@code request-timeout}; an open
     * trade window has no elapsed-time expiry at all, so {@code trade-timeout} described a feature
     * that does not exist. The setting is therefore removed (feature request UltiKits/UltiTrade#41).
     * The only timeout settings left are the request's own.
     */
    @Test
    @DisplayName("the only timeout settings declared are the trade request's (UltiKits/UltiTrade#18)")
    void declaresOnlyTheRequestTimeouts() {
        List<String> timeoutKeys = new ArrayList<>();
        for (String key : declaredKeys()) {
            if (key.contains("timeout")) {
                timeoutKeys.add(key);
            }
        }

        assertThat(timeoutKeys).containsExactlyInAnyOrder("request-timeout", "messages.request-timeout");
    }

    /**
     * UltiKits/UltiTrade#17. Seven {@code messages.*} keys are read by the module; six more, for the
     * replies of {@code /trade toggle}, {@code /trade block} and {@code /trade unblock}, never were.
     * Those replies moved into the language catalogue and the six keys were removed; the seven that
     * are read are unaffected, so they stay.
     */
    @Test
    @DisplayName("the message settings declared are exactly the seven the module reads (UltiKits/UltiTrade#17)")
    void declaresExactlyTheSevenReadMessages() {
        List<String> messageKeys = new ArrayList<>();
        for (String key : declaredKeys()) {
            if (key.startsWith("messages.")) {
                messageKeys.add(key);
            }
        }

        assertThat(messageKeys).containsExactlyInAnyOrder(
                "messages.request-sent",
                "messages.request-received",
                "messages.request-timeout",
                "messages.trade-complete",
                "messages.trade-cancelled",
                "messages.trade-disabled",
                "messages.player-blocked");
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValues {

        @Test
        @DisplayName("Should have correct basic settings defaults")
        void basicSettingsDefaults() {
            assertThat(config.getRequestTimeout()).isEqualTo(30);
            assertThat(config.getMaxDistance()).isEqualTo(50);
            assertThat(config.isAllowCrossWorld()).isFalse();
        }

        @Test
        @DisplayName("Should have correct trade features defaults")
        void tradeFeaturesDefaults() {
            assertThat(config.isEnableMoneyTrade()).isTrue();
            assertThat(config.isEnableExpTrade()).isTrue();
            assertThat(config.isEnableShiftClick()).isTrue();
        }

        @Test
        @DisplayName("Should have correct tax settings defaults")
        void taxSettingsDefaults() {
            assertThat(config.getTradeTax()).isEqualTo(0.0);
            assertThat(config.getExpTaxRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should have correct confirmation settings defaults")
        void confirmationSettingsDefaults() {
            assertThat(config.getConfirmThreshold()).isEqualTo(10000);
        }

        @Test
        @DisplayName("Should have correct log settings defaults")
        void logSettingsDefaults() {
            assertThat(config.isEnableTradeLog()).isTrue();
            assertThat(config.getLogRetentionDays()).isEqualTo(30);
            assertThat(config.getCleanupIntervalHours()).isEqualTo(24);
        }

        @Test
        @DisplayName("Should have correct effect settings defaults")
        void effectSettingsDefaults() {
            assertThat(config.isEnableSounds()).isTrue();
            assertThat(config.isEnableParticles()).isTrue();
            assertThat(config.isEnableBossbar()).isTrue();
            assertThat(config.isEnableClickableButtons()).isTrue();
        }

        @Test
        @DisplayName("Should have correct GUI settings defaults")
        void guiSettingsDefaults() {
            assertThat(config.getGuiTitle()).isEqualTo("&6与 {PLAYER} 交易");
        }

        @Test
        @DisplayName("Should have correct message defaults")
        void messageDefaults() {
            assertThat(config.getRequestSentMessage()).isNotEmpty();
            assertThat(config.getRequestReceivedMessage()).isNotEmpty();
            assertThat(config.getRequestTimeoutMessage()).isNotEmpty();
            assertThat(config.getTradeCompleteMessage()).isNotEmpty();
            assertThat(config.getTradeCancelledMessage()).isNotEmpty();
            assertThat(config.getTradeDisabledMessage()).isNotEmpty();
            assertThat(config.getPlayerBlockedMessage()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Setters and Getters")
    class SettersAndGetters {

        @Test
        @DisplayName("Should set and get request timeout")
        void requestTimeout() {
            config.setRequestTimeout(60);
            assertThat(config.getRequestTimeout()).isEqualTo(60);
        }

        @Test
        @DisplayName("Should set and get max distance")
        void maxDistance() {
            config.setMaxDistance(100);
            assertThat(config.getMaxDistance()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should set and get allow cross world")
        void allowCrossWorld() {
            config.setAllowCrossWorld(true);
            assertThat(config.isAllowCrossWorld()).isTrue();
        }

        @Test
        @DisplayName("Should set and get enable money trade")
        void enableMoneyTrade() {
            config.setEnableMoneyTrade(false);
            assertThat(config.isEnableMoneyTrade()).isFalse();
        }

        @Test
        @DisplayName("Should set and get trade tax")
        void tradeTax() {
            config.setTradeTax(0.05);
            assertThat(config.getTradeTax()).isEqualTo(0.05);
        }

        @Test
        @DisplayName("Should set and get confirm threshold")
        void confirmThreshold() {
            config.setConfirmThreshold(50000);
            assertThat(config.getConfirmThreshold()).isEqualTo(50000);
        }

        @Test
        @DisplayName("Should set and get enable trade log")
        void enableTradeLog() {
            config.setEnableTradeLog(false);
            assertThat(config.isEnableTradeLog()).isFalse();
        }

        @Test
        @DisplayName("Should set and get log retention days")
        void logRetentionDays() {
            config.setLogRetentionDays(60);
            assertThat(config.getLogRetentionDays()).isEqualTo(60);
        }

        @Test
        @DisplayName("Should set and get enable sounds")
        void enableSounds() {
            config.setEnableSounds(false);
            assertThat(config.isEnableSounds()).isFalse();
        }

        @Test
        @DisplayName("Should set and get enable exp trade")
        void enableExpTrade() {
            config.setEnableExpTrade(false);
            assertThat(config.isEnableExpTrade()).isFalse();
        }

        @Test
        @DisplayName("Should set and get enable shift click")
        void enableShiftClick() {
            config.setEnableShiftClick(false);
            assertThat(config.isEnableShiftClick()).isFalse();
        }

        @Test
        @DisplayName("Should set and get exp tax rate")
        void expTaxRate() {
            config.setExpTaxRate(0.15);
            assertThat(config.getExpTaxRate()).isEqualTo(0.15);
        }

        @Test
        @DisplayName("Should set and get cleanup interval hours")
        void cleanupIntervalHours() {
            config.setCleanupIntervalHours(48);
            assertThat(config.getCleanupIntervalHours()).isEqualTo(48);
        }

        @Test
        @DisplayName("Should set and get enable particles")
        void enableParticles() {
            config.setEnableParticles(false);
            assertThat(config.isEnableParticles()).isFalse();
        }

        @Test
        @DisplayName("Should set and get enable bossbar")
        void enableBossbar() {
            config.setEnableBossbar(false);
            assertThat(config.isEnableBossbar()).isFalse();
        }

        @Test
        @DisplayName("Should set and get enable clickable buttons")
        void enableClickableButtons() {
            config.setEnableClickableButtons(false);
            assertThat(config.isEnableClickableButtons()).isFalse();
        }

        @Test
        @DisplayName("Should set and get gui title")
        void guiTitle() {
            config.setGuiTitle("&eCustom Title");
            assertThat(config.getGuiTitle()).isEqualTo("&eCustom Title");
        }

        @Test
        @DisplayName("Should set and get request sent message")
        void requestSentMessage() {
            config.setRequestSentMessage("&aSent to {PLAYER}");
            assertThat(config.getRequestSentMessage()).isEqualTo("&aSent to {PLAYER}");
        }

        @Test
        @DisplayName("Should set and get request received message")
        void requestReceivedMessage() {
            config.setRequestReceivedMessage("&eRequest from {PLAYER}");
            assertThat(config.getRequestReceivedMessage()).isEqualTo("&eRequest from {PLAYER}");
        }

        @Test
        @DisplayName("Should set and get request timeout message")
        void requestTimeoutMessage() {
            config.setRequestTimeoutMessage("&cTimed out!");
            assertThat(config.getRequestTimeoutMessage()).isEqualTo("&cTimed out!");
        }

        @Test
        @DisplayName("Should set and get trade complete message")
        void tradeCompleteMessage() {
            config.setTradeCompleteMessage("&aDone!");
            assertThat(config.getTradeCompleteMessage()).isEqualTo("&aDone!");
        }

        @Test
        @DisplayName("Should set and get trade cancelled message")
        void tradeCancelledMessage() {
            config.setTradeCancelledMessage("&cCancelled!");
            assertThat(config.getTradeCancelledMessage()).isEqualTo("&cCancelled!");
        }

        @Test
        @DisplayName("Should set and get trade disabled message")
        void tradeDisabledMessage() {
            config.setTradeDisabledMessage("&cDisabled!");
            assertThat(config.getTradeDisabledMessage()).isEqualTo("&cDisabled!");
        }

        @Test
        @DisplayName("Should set and get player blocked message")
        void playerBlockedMessage() {
            config.setPlayerBlockedMessage("&cBlocked!");
            assertThat(config.getPlayerBlockedMessage()).isEqualTo("&cBlocked!");
        }
    }
}
