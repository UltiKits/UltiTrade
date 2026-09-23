package com.ultikits.plugins.trade.commands;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TradeCommand Tests")
class TradeCommandTest {

    private TradeCommand command;
    private TradeService tradeService;
    private TradeLogService logService;
    private Player player;
    private Player target;
    private UUID playerUuid;
    private UUID targetUuid;
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();

        tradeService = mock(TradeService.class);
        logService = mock(TradeLogService.class);
        answerI18nFrom("en");
        command = constructLikeTheContainer(
                UltiTradeTestHelper.getMockPlugin(), tradeService, logService);

        playerUuid = UUID.randomUUID();
        targetUuid = UUID.randomUUID();
        player = mock(Player.class);
        target = mock(Player.class);

        when(player.getName()).thenReturn("Player1");
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(target.getName()).thenReturn("Target");
        when(target.getUniqueId()).thenReturn(targetUuid);

        // Get the server mock from Bukkit (set up by UltiTradeTestHelper)
        server = Bukkit.getServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    /**
     * Builds the command the way the framework's container does: the declared constructor with the
     * most parameters, each parameter resolved by type from the beans on offer
     * ({@code SimpleContainer#createBeanWithConstructorInjection}). Written this way rather than as
     * a {@code new} expression so the same test runs against whatever constructor the command
     * declares, and so a constructor parameter the container could not resolve fails here.
     */
    private static TradeCommand constructLikeTheContainer(Object... beans) throws Exception {
        // Fully qualified: this class has a @Nested class named Constructor.
        java.lang.reflect.Constructor<?> widest = null;
        for (java.lang.reflect.Constructor<?> candidate : TradeCommand.class.getDeclaredConstructors()) {
            if (widest == null || candidate.getParameterCount() > widest.getParameterCount()) {
                widest = candidate;
            }
        }
        assertThat(widest).as("TradeCommand declares a constructor").isNotNull();
        Class<?>[] types = widest.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            for (Object bean : beans) {
                if (types[i].isInstance(bean)) {
                    args[i] = bean;
                    break;
                }
            }
            assertThat(args[i]).as("a bean for constructor parameter " + types[i].getName()).isNotNull();
        }
        return (TradeCommand) widest.newInstance(args);
    }

    /**
     * Makes the module's {@code i18n(key)} answer from this module's own shipped catalogue,
     * {@code lang/<code>.yml}, and return an unknown key unchanged, as the framework's
     * {@code Language#getLocalizedText} does.
     */
    private static YamlConfiguration answerI18nFrom(String code) throws Exception {
        YamlConfiguration catalogue = catalogue(code);
        when(UltiTradeTestHelper.getMockPlugin().i18n(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return catalogue.getString(key, key);
        });
        return catalogue;
    }

    private static YamlConfiguration catalogue(String code) throws Exception {
        String path = "/lang/" + code + ".yml";
        try (InputStream in = TradeCommandTest.class.getResourceAsStream(path)) {
            assertThat(in).as(path + " is on the classpath").isNotNull();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    /** What a player sees for a catalogue entry: {PLAYER} filled in, '&' colour codes applied. */
    private static String rendered(YamlConfiguration catalogue, String key, String playerName) {
        String text = catalogue.getString(key);
        assertThat(text).as("catalogue entry " + key).isNotBlank();
        return ChatColor.translateAlternateColorCodes('&', text.replace("{PLAYER}", playerName));
    }

    private static String rendered(YamlConfiguration catalogue, String key) {
        return rendered(catalogue, key, "");
    }

    /**
     * UltiKits/UltiTrade#17. The six replies below used to be Chinese literals in the command, and
     * {@code config/trade.yml} carried six {@code messages.*} keys for them that nothing read. The
     * maintainer's ruling (2026-09-22): the keys are removed and the text comes from the language
     * catalogue, so an English server answers in English and a Chinese one in Chinese. Each test
     * therefore requires the exact rendered catalogue entry, for both shipped languages -- a
     * substring of the old literal would pass against it and prove nothing.
     */
    @Nested
    @DisplayName("replies come from the language catalogue (UltiKits/UltiTrade#17)")
    class RepliesFromTheCatalogue {

        @ParameterizedTest(name = "lang/{0}.yml")
        @ValueSource(strings = {"en", "zh"})
        @DisplayName("/trade toggle, turning trading on, answers with trade_toggle_on")
        void toggleOn(String code) throws Exception {
            YamlConfiguration catalogue = answerI18nFrom(code);
            when(logService.toggleTrade(player)).thenReturn(true);

            command.toggle(player);

            verify(player).sendMessage(rendered(catalogue, "trade_toggle_on"));
            verify(player, times(1)).sendMessage(anyString());
        }

        @ParameterizedTest(name = "lang/{0}.yml")
        @ValueSource(strings = {"en", "zh"})
        @DisplayName("/trade toggle, turning trading off, answers with trade_toggle_off")
        void toggleOff(String code) throws Exception {
            YamlConfiguration catalogue = answerI18nFrom(code);
            when(logService.toggleTrade(player)).thenReturn(false);

            command.toggle(player);

            verify(player).sendMessage(rendered(catalogue, "trade_toggle_off"));
            verify(player, times(1)).sendMessage(anyString());
        }

        @ParameterizedTest(name = "lang/{0}.yml")
        @ValueSource(strings = {"en", "zh"})
        @DisplayName("/trade block answers with block_success, naming the blocked player")
        void blockSuccess(String code) throws Exception {
            YamlConfiguration catalogue = answerI18nFrom(code);
            when(server.getPlayerExact("Target")).thenReturn(target);
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(false);

            command.blockPlayer(player, "Target");

            verify(logService).blockPlayer(player, targetUuid);
            verify(player).sendMessage(rendered(catalogue, "block_success", "Target"));
        }

        @ParameterizedTest(name = "lang/{0}.yml")
        @ValueSource(strings = {"en", "zh"})
        @DisplayName("/trade block on an already-blocked player answers with already_blocked")
        void alreadyBlocked(String code) throws Exception {
            YamlConfiguration catalogue = answerI18nFrom(code);
            when(server.getPlayerExact("Target")).thenReturn(target);
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(true);

            command.blockPlayer(player, "Target");

            verify(player).sendMessage(rendered(catalogue, "already_blocked", "Target"));
            verify(player, times(1)).sendMessage(anyString());
            verify(logService, never()).blockPlayer(any(), any());
        }

        @ParameterizedTest(name = "lang/{0}.yml")
        @ValueSource(strings = {"en", "zh"})
        @DisplayName("/trade unblock answers with unblock_success, naming the unblocked player")
        void unblockSuccess(String code) throws Exception {
            YamlConfiguration catalogue = answerI18nFrom(code);
            when(server.getPlayerExact("Target")).thenReturn(target);
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(true);

            command.unblockPlayer(player, "Target");

            verify(logService).unblockPlayer(player, targetUuid);
            verify(player).sendMessage(rendered(catalogue, "unblock_success", "Target"));
            verify(player, times(1)).sendMessage(anyString());
        }

        @ParameterizedTest(name = "lang/{0}.yml")
        @ValueSource(strings = {"en", "zh"})
        @DisplayName("/trade unblock on a player who is not blocked answers with not_blocked")
        void notBlocked(String code) throws Exception {
            YamlConfiguration catalogue = answerI18nFrom(code);
            when(server.getPlayerExact("Target")).thenReturn(target);
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(false);

            command.unblockPlayer(player, "Target");

            verify(player).sendMessage(rendered(catalogue, "not_blocked", "Target"));
            verify(player, times(1)).sendMessage(anyString());
            verify(logService, never()).unblockPlayer(any(), any());
        }

        @Test
        @DisplayName("control: the two shipped catalogues answer differently for every one of the six keys")
        void theTwoCataloguesDiffer() throws Exception {
            // Without this, a test above could pass for both languages because both files happened
            // to hold the same text, and "follows the language setting" would be unproven.
            YamlConfiguration en = catalogue("en");
            YamlConfiguration zh = catalogue("zh");
            for (String key : new String[] {"trade_toggle_on", "trade_toggle_off", "block_success",
                    "unblock_success", "already_blocked", "not_blocked"}) {
                assertThat(en.getString(key)).as("en " + key).isNotBlank();
                assertThat(zh.getString(key)).as("zh " + key).isNotBlank();
                assertThat(en.getString(key)).as(key).isNotEqualTo(zh.getString(key));
            }
            for (String key : new String[] {"block_success", "unblock_success", "already_blocked",
                    "not_blocked"}) {
                assertThat(en.getString(key)).as("en " + key).contains("{PLAYER}");
                assertThat(zh.getString(key)).as("zh " + key).contains("{PLAYER}");
            }
        }
    }

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should store trade service")
        void storeTradeService() {
            assertThat(command).isNotNull();
        }

        @Test
        @DisplayName("Should store log service")
        void storeLogService() {
            assertThat(command).isNotNull();
        }
    }

    @Nested
    @DisplayName("sendRequest")
    class SendRequest {

        @Test
        @DisplayName("Should send trade request to online player")
        void sendRequestOnline() {
            when(server.getPlayerExact("Target")).thenReturn(target);

            command.sendRequest(player, "Target");

            verify(tradeService).sendRequest(player, target);
        }

        @Test
        @DisplayName("Should fail for offline player")
        void sendRequestOffline() {
            when(server.getPlayerExact("Offline")).thenReturn(null);

            command.sendRequest(player, "Offline");

            verify(player).sendMessage(contains("不在线"));
            verify(tradeService, never()).sendRequest(any(), any());
        }

        @Test
        @DisplayName("Should fail for self-trade")
        void sendRequestSelf() {
            // getPlayerExact returns `player` itself, so target.equals(sender) is true
            when(server.getPlayerExact("Player1")).thenReturn(player);

            command.sendRequest(player, "Player1");

            verify(player).sendMessage(contains("不能和自己交易"));
            verify(tradeService, never()).sendRequest(any(), any());
        }
    }

    @Nested
    @DisplayName("accept")
    class Accept {

        @Test
        @DisplayName("Should accept trade request")
        void accept() {
            command.accept(player);

            verify(tradeService).acceptRequest(player);
        }
    }

    @Nested
    @DisplayName("deny")
    class Deny {

        @Test
        @DisplayName("Should deny trade request")
        void deny() {
            command.deny(player);

            verify(tradeService).denyRequest(player);
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        @Test
        @DisplayName("Should cancel trade")
        void cancelTrading() {
            when(tradeService.isTrading(playerUuid)).thenReturn(true);

            command.cancel(player);

            verify(tradeService).cancelTrade(player);
        }

        @Test
        @DisplayName("Should fail if not trading")
        void cancelNotTrading() {
            when(tradeService.isTrading(playerUuid)).thenReturn(false);

            command.cancel(player);

            verify(player).sendMessage(contains("没有在交易"));
            verify(tradeService, never()).cancelTrade(player);
        }
    }

    @Nested
    @DisplayName("toggle")
    class Toggle {

        @Test
        @DisplayName("Should toggle trade on")
        void toggleOn() {
            when(logService.toggleTrade(player)).thenReturn(true);

            command.toggle(player);

            verify(logService).toggleTrade(player);
            verify(player).sendMessage(ChatColor.GREEN + "Trade enabled!");
        }

        @Test
        @DisplayName("Should toggle trade off")
        void toggleOff() {
            when(logService.toggleTrade(player)).thenReturn(false);

            command.toggle(player);

            verify(logService).toggleTrade(player);
            verify(player).sendMessage(ChatColor.YELLOW + "Trade disabled!");
        }
    }

    @Nested
    @DisplayName("blockPlayer")
    class BlockPlayer {

        @Test
        @DisplayName("Should block online player")
        void blockOnline() {
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(false);
            when(server.getPlayerExact("Target")).thenReturn(target);

            command.blockPlayer(player, "Target");

            verify(logService).blockPlayer(player, targetUuid);
            verify(player).sendMessage(ChatColor.GREEN + "Added Target to your trade blacklist!");
        }

        @Test
        @DisplayName("Should fail for offline player")
        void blockOffline() {
            when(server.getPlayerExact("Offline")).thenReturn(null);

            command.blockPlayer(player, "Offline");

            verify(player).sendMessage(contains("不在线"));
            verify(logService, never()).blockPlayer(any(), any());
        }

        @Test
        @DisplayName("Should fail for self-block")
        void blockSelf() {
            // getPlayerExact returns `player` itself, so target.equals(sender) is true
            when(server.getPlayerExact("Player1")).thenReturn(player);

            command.blockPlayer(player, "Player1");

            verify(player).sendMessage(contains("不能将自己添加到黑名单"));
            verify(logService, never()).blockPlayer(any(), any());
        }

        @Test
        @DisplayName("Should fail for already blocked")
        void blockAlreadyBlocked() {
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(true);
            when(server.getPlayerExact("Target")).thenReturn(target);

            command.blockPlayer(player, "Target");

            verify(player).sendMessage(ChatColor.RED + "Target is already in your blacklist!");
            verify(logService, never()).blockPlayer(any(), any());
        }
    }

    @Nested
    @DisplayName("unblockPlayer")
    class UnblockPlayer {

        @Test
        @DisplayName("Should unblock online player")
        void unblockOnline() {
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(true);
            when(server.getPlayerExact("Target")).thenReturn(target);

            command.unblockPlayer(player, "Target");

            verify(logService).unblockPlayer(player, targetUuid);
            verify(player).sendMessage(ChatColor.GREEN + "Removed Target from your trade blacklist!");
        }

        @Test
        @DisplayName("Should fail for offline player")
        void unblockOffline() {
            when(server.getPlayerExact("Offline")).thenReturn(null);

            command.unblockPlayer(player, "Offline");

            verify(player).sendMessage(contains("不在线"));
            verify(logService, never()).unblockPlayer(any(), any());
        }

        @Test
        @DisplayName("Should fail for not blocked")
        void unblockNotBlocked() {
            when(logService.isBlocked(playerUuid, targetUuid)).thenReturn(false);
            when(server.getPlayerExact("Target")).thenReturn(target);

            command.unblockPlayer(player, "Target");

            verify(player).sendMessage(ChatColor.RED + "Target is not in your blacklist!");
            verify(logService, never()).unblockPlayer(any(), any());
        }
    }

    @Nested
    @DisplayName("help")
    class Help {

        @Test
        @DisplayName("Should show help message")
        void showHelp() {
            when(logService.isTradeEnabled(playerUuid)).thenReturn(true);

            command.help(player);

            verify(player, atLeastOnce()).sendMessage(contains("UltiTrade"));
        }

        @Test
        @DisplayName("Should show current trade status")
        void showTradeStatus() {
            when(logService.isTradeEnabled(playerUuid)).thenReturn(false);

            command.help(player);

            verify(player).sendMessage(contains("交易状态"));
        }

        @Test
        @DisplayName("Should show trade enabled status in green")
        void showTradeEnabledStatus() {
            when(logService.isTradeEnabled(playerUuid)).thenReturn(true);

            command.help(player);

            verify(player).sendMessage(contains("已开启"));
        }

        @Test
        @DisplayName("Should show trade disabled status in red")
        void showTradeDisabledStatus() {
            when(logService.isTradeEnabled(playerUuid)).thenReturn(false);

            command.help(player);

            verify(player).sendMessage(contains("已关闭"));
        }

        @Test
        @DisplayName("Should show all available commands")
        void showAllCommands() {
            when(logService.isTradeEnabled(playerUuid)).thenReturn(true);

            command.help(player);

            verify(player).sendMessage(contains("/trade accept"));
            verify(player).sendMessage(contains("/trade deny"));
            verify(player).sendMessage(contains("/trade cancel"));
            verify(player).sendMessage(contains("/trade toggle"));
            verify(player).sendMessage(contains("/trade block"));
            verify(player).sendMessage(contains("/trade unblock"));
        }
    }

    @Nested
    @DisplayName("handleHelp")
    class HandleHelp {

        @Test
        @DisplayName("handleHelp should delegate to help for Player")
        void handleHelpForPlayer() {
            when(logService.isTradeEnabled(playerUuid)).thenReturn(true);

            command.handleHelp(player);

            verify(player, atLeastOnce()).sendMessage(contains("UltiTrade"));
        }

        @Test
        @DisplayName("handleHelp should not throw for non-Player sender")
        void handleHelpForNonPlayer() {
            org.bukkit.command.CommandSender consoleSender = mock(org.bukkit.command.CommandSender.class);

            // Should not throw - just silently return
            command.handleHelp(consoleSender);

            verify(consoleSender, never()).sendMessage(anyString());
        }
    }
}
