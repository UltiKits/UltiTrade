package com.ultikits.plugins.trade.i18n;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.commands.TradeCommand;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.TradeSession;
import com.ultikits.plugins.trade.gui.TradeConfirmPage;
import com.ultikits.plugins.trade.gui.TradeGUI;
import com.ultikits.plugins.trade.listener.TradeListener;
import com.ultikits.plugins.trade.placeholder.TradePlaceholderExpansion;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Text this module shows to players and writes to the console follows the framework's
 * {@code language} setting (UltiKits/UltiTrade#16).
 * <p>
 * The module's {@code i18n} answers from the catalogue this module really ships ({@link CatalogueText}).
 * Before the language sweep every player-facing line was fixed Chinese text, the console lines were
 * fixed English or Chinese text, and the enable and disable lines passed a Chinese sentence as the
 * key, while the catalogue already held English and Chinese text for most of it that no code read.
 * <p>
 * Chat lines are compared with their colour codes stripped: which colour a line carries is not what
 * this test pins, the words are.
 */
@DisplayName("UltiTrade text follows the language setting (UltiKits/UltiTrade#16)")
class TradeTextLanguageTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    /** The catalogue text for {@code key}, placeholders filled, colours stripped; or a marker naming the missing key. */
    private static String words(String code, String key, String... tokenValuePairs) {
        String value = CatalogueText.entries(code).get(key);
        if (value == null) {
            return "<lang/" + code + " has no " + key + ">";
        }
        for (int i = 0; i + 1 < tokenValuePairs.length; i += 2) {
            value = value.replace(tokenValuePairs[i], tokenValuePairs[i + 1]);
        }
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value));
    }

    /** The console text for {@code key}, placeholders filled; or a marker naming the missing key. */
    private static String console(String code, String key, String... tokenValuePairs) {
        String value = CatalogueText.entries(code).get(key);
        if (value == null) {
            return "<lang/" + code + " has no " + key + ">";
        }
        for (int i = 0; i + 1 < tokenValuePairs.length; i += 2) {
            value = value.replace(tokenValuePairs[i], tokenValuePairs[i + 1]);
        }
        return value;
    }

    /** Every line {@code player} was sent, colours stripped; none of them may hold Chinese text. */
    private static List<String> englishLinesSentTo(Player player) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> lines = new ArrayList<>();
        for (String line : captor.getAllValues()) {
            lines.add(ChatColor.stripColor(line));
        }
        assertThat(lines).noneMatch(l -> CJK.matcher(l).find());
        return lines;
    }

    /**
     * Makes the test server's item factory keep the meta a window writes: its {@code asMetaFor} is an
     * unstubbed mock answering {@code null}, which would drop every name and lore line on
     * {@code ItemStack#setItemMeta}.
     */
    private static void keepItemMeta() {
        org.bukkit.inventory.ItemFactory factory = Bukkit.getItemFactory();
        org.mockito.Mockito.lenient().when(factory.asMetaFor(any(ItemMeta.class), any(org.bukkit.Material.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(factory.asMetaFor(any(ItemMeta.class), any(ItemStack.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Every item name and lore line placed into {@code inventory}; none may hold Chinese text. */
    private static List<String> englishItemText(org.bukkit.inventory.Inventory inventory) {
        ArgumentCaptor<ItemStack> items = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory, atLeast(0)).setItem(org.mockito.ArgumentMatchers.anyInt(), items.capture());
        List<String> text = new ArrayList<>();
        for (ItemStack item : items.getAllValues()) {
            ItemMeta meta = item == null ? null : item.getItemMeta();
            if (meta == null) {
                continue;
            }
            if (meta.hasDisplayName()) {
                text.add(ChatColor.stripColor(meta.getDisplayName()));
            }
            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    text.add(ChatColor.stripColor(line));
                }
            }
        }
        assertThat(text).isNotEmpty();
        assertThat(text).noneMatch(l -> CJK.matcher(l).find());
        return text;
    }

    private UltiTrade plugin;
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
        plugin = UltiTradeTestHelper.getMockPlugin();
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        server = Bukkit.getServer();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    private TradeService serviceSpeakingEnglish() {
        TradeService service = mock(TradeService.class);
        TradeSeams.speak(service, "en");
        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        when(service.getConfig()).thenReturn(config);
        return service;
    }

    @Nested
    @DisplayName("/trade under language: en")
    class CommandInEnglish {

        private TradeService service;
        private TradeLogService logService;
        private TradeCommand command;
        private Player player;

        @BeforeEach
        void build() {
            service = serviceSpeakingEnglish();
            logService = mock(TradeLogService.class);
            command = new TradeCommand(plugin, service, logService);
            player = UltiTradeTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        }

        @Test
        @DisplayName("the command description is a key with English text")
        void description() {
            String key = TradeCommand.class.getAnnotation(CmdExecutor.class).description();

            assertThat(CatalogueText.entries("en").get(key)).isEqualTo("Player trading system");
        }

        @Test
        @DisplayName("help lists every subcommand and the trade status in English")
        void help() {
            when(logService.isTradeEnabled(player.getUniqueId())).thenReturn(true);

            command.help(player);

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "help_header"),
                    words("en", "help_request"),
                    words("en", "help_accept"),
                    words("en", "help_deny"),
                    words("en", "help_cancel"),
                    words("en", "help_toggle"),
                    words("en", "help_block"),
                    words("en", "help_unblock"),
                    "",
                    words("en", "help_status", "{STATUS}", words("en", "help_status_on")));
        }

        @Test
        @DisplayName("refusals are English: offline target, self, not trading, block and unblock refusals")
        void refusals() {
            doReturn(null).when(server).getPlayerExact("Ghost");
            doReturn(player).when(server).getPlayerExact("Steve");
            when(service.isTrading(player.getUniqueId())).thenReturn(false);

            command.sendRequest(player, "Ghost");
            command.sendRequest(player, "Steve");
            command.cancel(player);
            command.blockPlayer(player, "Ghost");
            command.blockPlayer(player, "Steve");
            command.unblockPlayer(player, "Ghost");

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "player_not_found", "{PLAYER}", "Ghost"),
                    words("en", "cannot_trade_self"),
                    words("en", "not_trading"),
                    words("en", "block_player_offline", "{PLAYER}", "Ghost"),
                    words("en", "cannot_block_self"),
                    words("en", "unblock_player_offline", "{PLAYER}", "Ghost"));
        }

        @Test
        @DisplayName("a successful block adds the English explanation")
        void blockHint() {
            Player target = UltiTradeTestHelper.createMockPlayer("Alex", UUID.randomUUID());
            doReturn(target).when(server).getPlayerExact("Alex");

            command.blockPlayer(player, "Alex");

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "block_success", "{PLAYER}", "Alex"),
                    words("en", "block_success_hint"));
        }
    }

    @Nested
    @DisplayName("trade requests and cancellations under language: en")
    class ServiceInEnglish {

        private TradeService service;
        private TradeConfig config;
        private TradeLogService logService;
        private Player alice;
        private Player bob;

        @BeforeEach
        void build() throws Exception {
            config = UltiTradeTestHelper.createDefaultConfig();
            logService = mock(TradeLogService.class);
            service = new TradeService();
            UltiTradeTestHelper.setField(service, "plugin", plugin);
            UltiTradeTestHelper.setField(service, "config", config);
            UltiTradeTestHelper.setField(service, "logService", logService);
            alice = UltiTradeTestHelper.createMockPlayer("Alice", UUID.randomUUID());
            bob = UltiTradeTestHelper.createMockPlayer("Bob", UUID.randomUUID());
            UUID aliceId = alice.getUniqueId();
            doReturn(alice).when(server).getPlayer(aliceId);
            UUID bobId = bob.getUniqueId();
            doReturn(bob).when(server).getPlayer(bobId);
        }

        @Test
        @DisplayName("the sender's own toggle refusal is English")
        void senderToggledOff() {
            when(logService.isTradeEnabled(alice.getUniqueId())).thenReturn(false);

            service.sendRequest(alice, bob);

            assertThat(englishLinesSentTo(alice)).containsExactly(words("en", "sender_trade_disabled"));
        }

        @Test
        @DisplayName("refusing a player one has blocked is English")
        void blockedTarget() {
            when(logService.isTradeEnabled(any())).thenReturn(true);
            when(logService.isBlocked(bob.getUniqueId(), alice.getUniqueId())).thenReturn(false);
            when(logService.isBlocked(alice.getUniqueId(), bob.getUniqueId())).thenReturn(true);

            service.sendRequest(alice, bob);

            assertThat(englishLinesSentTo(alice)).containsExactly(words("en", "you_blocked_target", "{PLAYER}", "Bob"));
        }

        @Test
        @DisplayName("accepting or denying with nothing pending is English")
        void nothingPending() {
            service.acceptRequest(alice);
            service.denyRequest(alice);

            assertThat(englishLinesSentTo(alice)).containsExactly(
                    words("en", "no_pending_request"),
                    words("en", "no_pending_request"));
        }

        @Test
        @DisplayName("a player's own cancellation reason is English")
        void playerCancelled() throws Exception {
            // messages.trade-cancelled as this build writes it under language: en
            when(config.getTradeCancelledMessage()).thenReturn(CatalogueText.entries("en").get("trade_cancelled"));
            TradeSession session = new TradeSession(alice, bob);
            @SuppressWarnings("unchecked")
            Map<UUID, TradeSession> sessions = (Map<UUID, TradeSession>) read(service, "activeSessions");
            @SuppressWarnings("unchecked")
            Map<UUID, UUID> bySession = (Map<UUID, UUID>) read(service, "playerSessionMap");
            sessions.put(session.getSessionId(), session);
            bySession.put(alice.getUniqueId(), session.getSessionId());
            bySession.put(bob.getUniqueId(), session.getSessionId());

            service.cancelTrade(alice);

            assertThat(englishLinesSentTo(bob)).contains(words("en", "trade_cancelled") + " ("
                    + words("en", "cancel_reason_player_cancelled", "{PLAYER}", "Alice") + ")");
        }
    }

    @Nested
    @DisplayName("trade windows under language: en")
    class WindowsInEnglish {

        @Test
        @DisplayName("every item name and lore line of the trade window is English")
        void tradeWindow() {
            TradeService service = serviceSpeakingEnglish();
            when(service.hasEconomy()).thenReturn(true);
            when(service.getConfig().getTradeTax()).thenReturn(0.1);
            when(service.getConfig().getExpTaxRate()).thenReturn(0.1);
            Player alice = UltiTradeTestHelper.createMockPlayer("Alice", UUID.randomUUID());
            Player bob = UltiTradeTestHelper.createMockPlayer("Bob", UUID.randomUUID());
            UUID aliceId = alice.getUniqueId();
            doReturn(alice).when(server).getPlayer(aliceId);
            UUID bobId = bob.getUniqueId();
            doReturn(bob).when(server).getPlayer(bobId);
            TradeSession session = new TradeSession(alice, bob);
            session.setMoney(alice.getUniqueId(), 100);
            session.setExp(alice.getUniqueId(), 50);

            keepItemMeta();
            TradeGUI gui = new TradeGUI(service, session, alice);

            assertThat(englishItemText(gui.getInventory())).contains(
                    words("en", "gui_your_items"),
                    words("en", "gui_their_items"),
                    words("en", "gui_cancel"));
        }

        @Test
        @DisplayName("every item name and lore line of the confirmation page is English")
        void confirmationPage() {
            TradeService service = serviceSpeakingEnglish();
            Player alice = UltiTradeTestHelper.createMockPlayer("Alice", UUID.randomUUID());
            Player bob = UltiTradeTestHelper.createMockPlayer("Bob", UUID.randomUUID());
            UUID aliceId = alice.getUniqueId();
            doReturn(alice).when(server).getPlayer(aliceId);
            UUID bobId = bob.getUniqueId();
            doReturn(bob).when(server).getPlayer(bobId);
            TradeSession session = new TradeSession(alice, bob);

            keepItemMeta();
            TradeConfirmPage page = new TradeConfirmPage(service, session, alice, () -> { }, () -> { });

            assertThat(englishItemText(page.getInventory())).contains(words("en", "confirm_info_title"));
        }
    }

    @Nested
    @DisplayName("amount input under language: en")
    class InputInEnglish {

        @Test
        @DisplayName("an invalid, a negative and a cancelled amount are answered in English")
        void input() throws Exception {
            TradeService service = serviceSpeakingEnglish();
            TradeListener listener = new TradeListener();
            UltiTradeTestHelper.setField(listener, "tradeService", service);
            UltiTradeTestHelper.setField(listener, "config", service.getConfig());
            Player player = UltiTradeTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            for (String typed : new String[] {"abc", "-5", "cancel"}) {
                waitForMoney(listener, player.getUniqueId());
                listener.onPlayerChat(new AsyncPlayerChatEvent(false, player, typed, new HashSet<>()));
            }

            assertThat(englishLinesSentTo(player)).containsExactly(
                    words("en", "invalid_amount"),
                    words("en", "amount_negative"),
                    words("en", "input_cancelled"));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void waitForMoney(TradeListener listener, UUID uuid) throws Exception {
            Field field = TradeListener.class.getDeclaredField("waitingForInput");
            field.setAccessible(true);
            Class<?> inputType = Class.forName(TradeListener.class.getName() + "$InputType");
            Object money = Enum.valueOf((Class) inputType, "MONEY");
            ((Map<UUID, Object>) field.get(listener)).put(uuid, money);
        }
    }

    @Nested
    @DisplayName("placeholders under language: en")
    class PlaceholdersInEnglish {

        @Test
        @DisplayName("the display placeholders are English")
        void display() {
            TradeService service = serviceSpeakingEnglish();
            TradeLogService logService = mock(TradeLogService.class);
            PlayerTradeSettings stats = mock(PlayerTradeSettings.class);
            OfflinePlayer player = mock(OfflinePlayer.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(logService.getPlayerStats(any())).thenReturn(stats);
            when(stats.isTradeEnabled()).thenReturn(true);
            when(stats.getLastTradeTime()).thenReturn(0L);
            TradePlaceholderExpansion expansion = new TradePlaceholderExpansion(service, logService);

            assertThat(expansion.onRequest(player, "enabled_display")).isEqualTo(words("en", "placeholder_enabled"));
            assertThat(expansion.onRequest(player, "last_trade_time")).isEqualTo(words("en", "placeholder_never_traded"));
            assertThat(expansion.onRequest(player, "last_trade_ago")).isEqualTo(words("en", "placeholder_never"));
        }
    }

    @Nested
    @DisplayName("console lines")
    class Console {

        @Test
        @DisplayName("the enable line is English under language: en")
        void enableLine() {
            UltiTrade module = mock(UltiTrade.class);
            PluginLogger logger = mock(PluginLogger.class);
            when(module.getLogger()).thenReturn(logger);
            when(module.getContext()).thenReturn(mock(SimpleContainer.class));
            when(module.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            when(module.registerSelf()).thenCallRealMethod();

            module.registerSelf();

            verify(logger).info(console("en", "trade_enabled"));
        }

        @Test
        @DisplayName("the removed-key warning is Chinese under language: zh")
        void removedKeyWarning(@org.junit.jupiter.api.io.TempDir File dir) throws Exception {
            File file = new File(dir, "trade.yml");
            Files.write(file.toPath(), "trade-timeout: 120\n".getBytes(StandardCharsets.UTF_8));
            UltiTrade module = mock(UltiTrade.class);
            when(module.i18n(anyString())).thenAnswer(CatalogueText.answer("zh"));
            List<String> warnings = new ArrayList<>();

            TradeSeams.warnAboutLeftovers(file, warnings::add, module);

            assertThat(warnings).containsExactly(console("zh", "removed_key_warning",
                    "{FILE}", file.getPath(),
                    "{KEY}", "trade-timeout",
                    "{REASON}", console("zh", "removed_key_reason_trade_timeout")));
        }
    }

    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
