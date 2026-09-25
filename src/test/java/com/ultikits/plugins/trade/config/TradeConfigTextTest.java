package com.ultikits.plugins.trade.config;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.interfaces.ConfigChangeListener;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code config/trade.yml} holds the trade-window title and every message in the server's language, and
 * the module sends exactly what the file holds (maintainer decision 2026-09-25; UltiKits/UltiTrade#16).
 * A value that is still built-in text — any language's text from this jar, or the default an earlier
 * version shipped — follows {@code language} at enable and on reload, in both
 * directions; anything else is the operator's and is kept byte for byte. Every case runs the framework's
 * real {@code AbstractConfigEntity#init} on a temporary folder, the module's real {@code registerSelf()}
 * and {@code onReload()}, and answers {@code i18n} from the module's real catalogues.
 */
@DisplayName("trade.yml holds the window title and messages in the server's language (UltiKits/UltiTrade#16)")
class TradeConfigTextTest {

    /** One text setting: its field, its path in trade.yml, its catalogue key, its colour, its shipped default. */
    private static final class Setting {
        final String field;
        final String path;
        final String key;
        final String prefix;
        final String shipped;

        Setting(String field, String path, String key, String prefix, String shipped) {
            this.field = field;
            this.path = path;
            this.key = key;
            this.prefix = prefix;
            this.shipped = shipped;
        }

        String text(String code) {
            return prefix + CatalogueText.text(code, key);
        }

        String getter() {
            return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        }
    }

    /** The 8 settings, with the one default each shipped in every earlier version (census §4). */
    private static final List<Setting> SETTINGS = Arrays.asList(
            new Setting("guiTitle", "gui-title", "gui_title", "", "&6与 {PLAYER} 交易"),
            new Setting("requestSentMessage", "messages.request-sent", "message_request_sent", "", "&a已向 &f{PLAYER} &a发送交易请求！"),
            new Setting("requestReceivedMessage", "messages.request-received", "message_request_received", "", "&e{PLAYER} &f请求与你交易！输入 /trade accept 接受"),
            new Setting("requestTimeoutMessage", "messages.request-timeout", "request_timeout", "", "&c交易请求已超时！"),
            new Setting("tradeCompleteMessage", "messages.trade-complete", "trade_complete", "", "&a交易完成！"),
            new Setting("tradeCancelledMessage", "messages.trade-cancelled", "trade_cancelled", "", "&c交易已取消！"),
            new Setting("tradeDisabledMessage", "messages.trade-disabled", "message_trade_disabled", "", "&c对方已关闭交易功能！"),
            new Setting("playerBlockedMessage", "messages.player-blocked", "message_player_blocked", "", "&c对方已将你加入黑名单！"));

    /** The fields that carried {@code @NotEmpty} at origin/master: exactly the 8 above. */
    private static final Set<String> NOT_EMPTY_AT_MASTER = new TreeSet<>();

    static {
        for (Setting s : SETTINGS) {
            NOT_EMPTY_AT_MASTER.add(s.field);
        }
    }

    private static final String[] LANGUAGES = {"en", "zh"};

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @TempDir
    Path tempDir;

    private final String[] language = {"en"};

    private final PluginLogger logger = mock(PluginLogger.class);

    /** The configuration the module double returns from {@code getConfig(TradeConfig.class)}. */
    private TradeConfig current;

    private UltiTrade plugin;

    @BeforeEach
    void setUp() {
        plugin = moduleDouble();
    }

    @AfterEach
    void tearDown() {
        current = null;
    }

    @Test
    @DisplayName("the catalogues give each setting English text under en and exactly its shipped default under zh")
    void catalogueTexts() {
        for (Setting s : SETTINGS) {
            assertThat(s.text("zh")).as(s.field).isEqualTo(s.shipped);
            assertThat(s.text("en")).as(s.field).startsWith(s.prefix).doesNotMatch(".*" + CJK.pattern() + ".*");
        }
    }

    @Test
    @DisplayName("fresh start under en: trade.yml holds every setting's English text, and each getter returns the file's value")
    void freshStartEnglish() throws Exception {
        language[0] = "en";
        TradeConfig config = spy(load());

        start(config);

        YamlConfiguration disk = onDisk();
        for (Setting s : SETTINGS) {
            assertThat(disk.getString(s.path)).as(s.path).isEqualTo(s.text("en"));
            assertThat(get(config, s)).as(s.field).isEqualTo(disk.getString(s.path));
        }
        verify(config, times(1)).save();
    }

    @Test
    @DisplayName("fresh start under zh: trade.yml holds every setting's Chinese text, which is its shipped default, and the module writes nothing")
    void freshStartChinese() throws Exception {
        language[0] = "zh";
        TradeConfig config = spy(load());
        byte[] afterFramework = bytes();

        start(config);

        YamlConfiguration disk = onDisk();
        for (Setting s : SETTINGS) {
            assertThat(disk.getString(s.path)).as(s.path).isEqualTo(s.shipped);
            assertThat(get(config, s)).as(s.field).isEqualTo(s.shipped);
        }
        verify(config, never()).save();
        assertThat(bytes()).isEqualTo(afterFramework);
    }

    @Test
    @DisplayName("every built-in text in the file (shipped default, jar en text, jar zh text) is replaced with the current language's text and saved, under en and zh")
    void everyTrackedValueFollowsTheLanguage() throws Exception {
        for (String code : LANGUAGES) {
            for (String member : new String[] {"shipped", "en", "zh"}) {
                language[0] = code;
                Map<String, String> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    values.put(s.path, "shipped".equals(member) ? s.shipped : s.text(member));
                }
                write(values);
                TradeConfig config = spy(load());

                start(config);

                YamlConfiguration disk = onDisk();
                for (Setting s : SETTINGS) {
                    String what = "language " + code + ", file held the " + member + " text of " + s.path;
                    assertThat(disk.getString(s.path)).as(what).isEqualTo(s.text(code));
                    assertThat(get(config, s)).as(what).isEqualTo(s.text(code));
                }
                boolean alreadyCurrent = member.equals(code) || ("shipped".equals(member) && "zh".equals(code));
                verify(config, times(alreadyCurrent ? 0 : 1)).save();
            }
        }
    }

    @Test
    @DisplayName("an upgraded file holding the shipped defaults reads exactly the English text under en (pinned, not read from the catalogue)")
    void upgradedFileReadsExactEnglish() throws Exception {
        language[0] = "en";
        Map<String, String> values = new LinkedHashMap<>();
        for (Setting s : SETTINGS) {
            values.put(s.path, s.shipped);
        }
        write(values);
        TradeConfig config = spy(load());

        start(config);

        YamlConfiguration disk = onDisk();
        assertThat(disk.getString("gui-title")).isEqualTo("&6Trade with {PLAYER}");
        assertThat(disk.getString("messages.request-sent")).isEqualTo("&aTrade request sent to &f{PLAYER}&a!");
        assertThat(disk.getString("messages.request-received")).isEqualTo("&e{PLAYER} &fwants to trade with you! Type /trade accept to accept");
        assertThat(disk.getString("messages.trade-disabled")).isEqualTo("&cThat player has trading disabled!");
        assertThat(disk.getString("messages.player-blocked")).isEqualTo("&cThat player has blocked you!");
        assertThat(disk.getString("messages.trade-complete")).isEqualTo("&aTrade completed!");
        assertThat(config.getRequestSentMessage()).isEqualTo("&aTrade request sent to &f{PLAYER}&a!");
        verify(config, times(1)).save();
    }

    @Test
    @DisplayName("the four settings whose earlier catalogue text differed keep their shipped Chinese text under zh, byte for byte")
    void newKeysKeepTheShippedChineseText() throws Exception {
        for (Setting s : SETTINGS) {
            if (s.key.startsWith("message_")) {
                assertThat(CatalogueText.text("zh", s.key)).as(s.key).isEqualTo(s.shipped);
            }
        }
    }

    @Test
    @DisplayName("a customised value, or built-in text changed by one character, is kept byte for byte under both languages and the file is not rewritten")
    void customisedValuesAreKept() throws Exception {
        for (String code : LANGUAGES) {
            for (String variant : new String[] {"shipped!", "en!", "own"}) {
                language[0] = code;
                Map<String, String> values = new LinkedHashMap<>();
                for (Setting s : SETTINGS) {
                    String v;
                    if ("shipped!".equals(variant)) {
                        v = s.shipped + "!";
                    } else if ("en!".equals(variant)) {
                        v = s.text("en") + " ";
                    } else {
                        v = "&dOperator text for " + s.field;
                    }
                    values.put(s.path, v);
                }
                write(values);
                TradeConfig config = spy(load());
                byte[] before = bytes();

                start(config);

                assertThat(bytes()).as(code + " " + variant).isEqualTo(before);
                for (Setting s : SETTINGS) {
                    assertThat(get(config, s)).as(code + " " + variant + " " + s.field).isEqualTo(values.get(s.path));
                }
                verify(config, never()).save();
            }
        }
    }

    @Test
    @DisplayName("a second enable with the same language writes nothing")
    void secondEnableWritesNothing() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, s.shipped);
            }
            write(values);
            start(load());
            byte[] afterFirst = bytes();

            TradeConfig second = spy(load());
            start(second);

            assertThat(bytes()).as(code).isEqualTo(afterFirst);
            verify(second, never()).save();
        }
    }

    @Test
    @DisplayName("onReload() after a language switch rewrites every setting in the new language, in both directions")
    void reloadFollowsALanguageSwitchBothWays() throws Exception {
        for (String[] direction : new String[][] {{"en", "zh"}, {"zh", "en"}}) {
            language[0] = direction[0];
            Files.deleteIfExists(file().toPath());
            TradeConfig config = load();
            start(config);

            language[0] = direction[1];
            config.init(plugin);
            reload();

            YamlConfiguration disk = onDisk();
            for (Setting s : SETTINGS) {
                String what = direction[0] + " -> " + direction[1] + ": " + s.path;
                assertThat(disk.getString(s.path)).as(what).isEqualTo(s.text(direction[1]));
                assertThat(get(config, s)).as(what).isEqualTo(s.text(direction[1]));
            }
        }
    }

    @Test
    @DisplayName("no configuration change listener rewrites the text (the framework fires them before it reloads the language)")
    void changeListenersDoNotMaterialize() throws Exception {
        language[0] = "en";
        TradeConfig config = load();
        start(config);
        byte[] before = bytes();

        language[0] = "zh";
        for (ConfigChangeListener listener : new ArrayList<>(config.getChangeListeners())) {
            listener.onConfigReload(config);
        }

        assertThat(bytes()).isEqualTo(before);
        assertThat(config.getTradeCompleteMessage()).isEqualTo(SETTINGS.get(4).text("en"));
    }

    @Test
    @DisplayName("an operator-edited language file on disk does not widen what counts as built-in text")
    void diskCatalogueDoesNotWidenTheTrackedSet() throws Exception {
        Path lang = Files.createDirectories(tempDir.resolve("lang"));
        StringBuilder yml = new StringBuilder();
        for (Setting s : SETTINGS) {
            yml.append(s.key).append(": \"Edited ").append(s.key).append("\"\n");
        }
        for (String code : LANGUAGES) {
            Files.write(lang.resolve(code + ".yml"), yml.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (String code : LANGUAGES) {
            language[0] = code;
            Map<String, String> values = new LinkedHashMap<>();
            for (Setting s : SETTINGS) {
                values.put(s.path, s.prefix + "Edited " + s.key);
            }
            write(values);
            TradeConfig config = spy(load());

            start(config);

            for (Setting s : SETTINGS) {
                assertThat(onDisk().getString(s.path)).as(code + " " + s.path).isEqualTo(values.get(s.path));
            }
            verify(config, never()).save();
        }
    }

    @Test
    @DisplayName("a file that cannot be saved is reported in the server's language, and the module still uses the new text")
    void saveFailureIsReported() throws Exception {
        language[0] = "en";
        TradeConfig config = spy(load());
        doThrow(new IOException("read-only")).when(config).save();

        assertThat(start(config)).isTrue();

        String expected = CatalogueText.text("en", "log_config_default_save_failed").replace("{FILE}", TradeConfig.CONFIG_FILE);
        verify(logger).warn(any(IOException.class), eq(expected));
        assertThat(config.getTradeCompleteMessage()).isEqualTo(SETTINGS.get(4).text("en"));
    }

    @Test
    @DisplayName("TradeService's request-sent and trade-disabled lines are rendered from the file's text")
    void tradeServiceLinesUseTheFile() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            Files.deleteIfExists(file().toPath());
            TradeConfig config = load();
            start(config);
            config.setMaxDistance(0);
            config.setEnableBossbar(false);
            config.setEnableClickableButtons(false);
            config.setEnableSounds(false);
            YamlConfiguration disk = onDisk();

            TradeLogService logService = mock(TradeLogService.class);
            TradeService service = new TradeService();
            set(service, "plugin", plugin);
            set(service, "config", config);
            set(service, "logService", logService);
            Player alice = player("Alice");
            Player bob = player("Bob");

            when(logService.isTradeEnabled(alice.getUniqueId())).thenReturn(true);
            when(logService.isTradeEnabled(bob.getUniqueId())).thenReturn(false);
            assertThat(service.sendRequest(alice, bob)).isFalse();
            verify(alice).sendMessage(ChatColor.translateAlternateColorCodes('&',
                    disk.getString("messages.trade-disabled").replace("{PLAYER}", "Bob")));

            when(logService.isTradeEnabled(bob.getUniqueId())).thenReturn(true);
            assertThat(service.sendRequest(alice, bob)).as(code).isTrue();
            verify(alice).sendMessage(ChatColor.translateAlternateColorCodes('&',
                    disk.getString("messages.request-sent").replace("{PLAYER}", "Bob")));
            verify(bob).sendMessage(ChatColor.translateAlternateColorCodes('&',
                    disk.getString("messages.request-received").replace("{PLAYER}", "Alice")));
        }
    }

    private static Player player(String name) {
        Player p = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(p.getUniqueId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        return p;
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("@NotEmpty is on exactly the fields that carried it at origin/master, and each text field's Java default is its shipped default")
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void validationAndJavaDefaults() throws Exception {
        Set<String> notEmpty = new TreeSet<>();
        for (Field f : TradeConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConfigEntry.class) && f.isAnnotationPresent(NotEmpty.class)) {
                notEmpty.add(f.getName());
            }
        }
        assertThat(notEmpty).containsExactlyElementsOf(NOT_EMPTY_AT_MASTER);

        TradeConfig fresh = new TradeConfig();
        for (Setting s : SETTINGS) {
            Field f = TradeConfig.class.getDeclaredField(s.field);
            f.setAccessible(true);
            assertThat(f.get(fresh)).as(s.field).isEqualTo(s.shipped);
            assertThat(f.getAnnotation(ConfigEntry.class).path()).as(s.field).isEqualTo(s.path);
        }
    }

    // ---- harness ----

    private static String get(TradeConfig config, Setting s) throws Exception {
        return (String) TradeConfig.class.getMethod(s.getter()).invoke(config);
    }

    private File file() {
        return new File(tempDir.toFile(), TradeConfig.CONFIG_FILE);
    }

    private byte[] bytes() throws IOException {
        return Files.readAllBytes(file().toPath());
    }

    private YamlConfiguration onDisk() {
        return YamlConfiguration.loadConfiguration(file());
    }

    /** Writes a trade.yml holding {@code values} (path to value), as an earlier version or an operator left it. */
    private void write(Map<String, String> values) throws IOException {
        Files.createDirectories(file().getParentFile().toPath());
        YamlConfiguration persisted = new YamlConfiguration();
        for (Map.Entry<String, String> e : values.entrySet()) {
            persisted.set(e.getKey(), e.getValue());
        }
        persisted.save(file());
    }

    /** The framework's own load: {@code init} fills missing keys with the Java defaults, saves, validates. */
    private TradeConfig load() throws IOException {
        Files.createDirectories(file().getParentFile().toPath());
        TradeConfig config = new TradeConfig();
        config.init(plugin);
        return config;
    }

    /** The module's enable path: {@code UltiTrade#registerSelf()} with {@code config} as the module's configuration. */
    private boolean start(TradeConfig config) {
        current = config;
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            return plugin.registerSelf();
        }
    }

    /** The module's {@code onReload()} (protected), as the framework calls it after rebuilding the language. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void reload() throws Exception {
        java.lang.reflect.Method onReload = UltiTrade.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            onReload.invoke(plugin);
        }
    }

    /**
     * A module double whose {@code registerSelf()} and {@code onReload()} are the real ones, whose
     * configuration folder is the temporary directory, whose {@code i18n} answers from the module's real
     * catalogue for the language in {@link #language} (read at call time), and whose
     * {@code getConfig(TradeConfig.class)} is {@link #current}.
     */
    private UltiTrade moduleDouble() {
        return Mockito.mock(UltiTrade.class, this::moduleAnswer);
    }

    private Object moduleAnswer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
        final Map<String, org.mockito.stubbing.Answer<String>> answers = new LinkedHashMap<>();
        for (String code : LANGUAGES) {
            answers.put(code, CatalogueText.answer(code));
        }
        {
            String name = invocation.getMethod().getName();
            switch (name) {
                case "registerSelf":
                case "onReload":
                    return invocation.callRealMethod();
                case "getConfigFolder":
                    return tempDir.toString();
                case "getConfigFile":
                    return new File(tempDir.toFile(), invocation.<String>getArgument(0));
                case "operatorConfigFile":
                    return file();
                case "i18n":
                    return answers.get(language[0]).answer(invocation);
                case "getLogger":
                    return logger;
                case "getConfig":
                    return current;
                case "getContext":
                    return mock(invocation.getMethod().getReturnType());
                default:
                    return Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        }
    }
}
