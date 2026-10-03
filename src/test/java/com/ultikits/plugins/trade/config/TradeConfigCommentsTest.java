package com.ultikits.plugins.trade.config;

import com.ultikits.plugins.trade.UltiTrade;
import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntry;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiTrade#51: under {@code language: en} a fresh install writes {@code config/trade.yml} with
 * English comments. Every comment that used to be Chinese is now one {@code {key}} token that the framework
 * resolves through this module's catalogue (UltiTools-Reborn#542), in the server's language.
 * <p>
 * These cases load the real {@link TradeConfig} through the framework's own {@code init} against the module's
 * real catalogues, so a token whose key is missing, misspelt or empty in a catalogue shows up as the token
 * itself in the file, which is what the assertions below look for.
 */
@DisplayName("config/trade.yml comments follow the server language (UltiKits/UltiTrade#51)")
class TradeConfigCommentsTest {

    /** The settings whose comments were Chinese: the adoption's scope, counted at master 17e5a6e. */
    private static final int TOKEN_COMMENTS = 24;

    @TempDir
    Path tempDir;

    /** Every {@code @ConfigEntry} of the entity: path -> declared comment. */
    private static Map<String, String> declaredComments() {
        Map<String, String> comments = new LinkedHashMap<>();
        for (Field f : TradeConfig.class.getDeclaredFields()) {
            ConfigEntry entry = f.getAnnotation(ConfigEntry.class);
            if (entry != null) {
                comments.put(entry.path(), entry.comment());
            }
        }
        return comments;
    }

    private static boolean isToken(String comment) {
        return comment.trim().matches("\\{[^{}]+}");
    }

    private static String keyOf(String token) {
        String trimmed = token.trim();
        return trimmed.substring(1, trimmed.length() - 1);
    }

    private static boolean containsHan(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN
                || (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF));
    }

    private File file() {
        return new File(tempDir.toFile(), TradeConfig.CONFIG_FILE);
    }

    private YamlConfiguration fileOnDisk() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.load(file());
        return yaml;
    }

    /** A mock module whose configuration folder is the temporary directory and whose {@code i18n} answers in {@code language}. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // getConfigFile is protected final on the framework base class
    private UltiTrade plugin(String language) {
        try {
            UltiTrade plugin = mock(UltiTrade.class);
            when(plugin.getPluginName()).thenReturn("UltiTrade");
            when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
            Method configFile = UltiToolsPlugin.class.getDeclaredMethod("getConfigFile", String.class);
            configFile.setAccessible(true);
            when(configFile.invoke(plugin, anyString())).thenAnswer(
                    inv -> new File(tempDir.toFile(), inv.<String>getArgument(0)));
            return plugin;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot stub the module's configuration folder", e);
        }
    }

    /** Loads (and, on a fresh folder, writes) the configuration file as the framework does at module start. */
    private static TradeConfig load(UltiToolsPlugin plugin) throws IOException {
        TradeConfig config = new TradeConfig();
        config.init(plugin);
        return config;
    }

    private void assertCommentsIn(String language) throws Exception {
        YamlConfiguration yaml = fileOnDisk();
        int checked = 0;
        for (Map.Entry<String, String> e : declaredComments().entrySet()) {
            if (!isToken(e.getValue())) {
                continue;
            }
            String expected = CatalogueText.text(language, keyOf(e.getValue()));
            assertThat(yaml.getComments(e.getKey())).as("comment of " + e.getKey() + " under " + language)
                    .containsExactly(expected);
            checked++;
        }
        assertThat(checked).isEqualTo(TOKEN_COMMENTS);
    }

    @Test
    @DisplayName("control: the entity declares twenty-four token comments, and none of its comments is Chinese")
    void twentyFourTokensAndNoChineseComment() {
        Map<String, String> comments = declaredComments();
        int tokens = 0;
        for (Map.Entry<String, String> e : comments.entrySet()) {
            if (isToken(e.getValue())) {
                tokens++;
            }
            assertThat(containsHan(e.getValue())).as("the comment of " + e.getKey()).isFalse();
        }
        assertThat(comments).hasSize(TOKEN_COMMENTS);
        assertThat(tokens).isEqualTo(TOKEN_COMMENTS);
    }

    @Test
    @DisplayName("a fresh install under language: en writes every one of the twenty-four comments in English")
    void freshInstallWritesEnglishComments() throws Exception {
        load(plugin("en"));

        assertCommentsIn("en");
        for (String line : Files.readAllLines(file().toPath(), StandardCharsets.UTF_8)) {
            if (line.trim().startsWith("#")) {
                assertThat(containsHan(line)).as("a comment line under en holds no Chinese: " + line).isFalse();
                assertThat(line).as("no unresolved token is left in the file").doesNotContain("{config_comment_");
            }
        }
    }

    @Test
    @DisplayName("a fresh install under language: zh writes every one of the twenty-four comments in Chinese")
    void freshInstallWritesChineseComments() throws Exception {
        load(plugin("zh"));

        assertCommentsIn("zh");
    }

    @Test
    @DisplayName("an upgraded file written with the old Chinese comments gets English ones at the next start, keeps its values, and then stays byte-identical")
    void upgradedFileSwitchesToTheServerLanguageAndKeepsValues() throws Exception {
        // The file the previous release wrote: the old Chinese comments, and an operator's edited values.
        YamlConfiguration old = new YamlConfiguration();
        old.options().parseComments(true);
        old.set("request-timeout", 45);
        old.setComments("request-timeout", Collections.singletonList("交易请求超时时间（秒）"));
        old.set("trade-tax", 0.25);
        old.setComments("trade-tax", Collections.singletonList("金币交易税率（0-1之间，0为不收税）"));
        Files.createDirectories(file().getParentFile().toPath());
        old.save(file());
        UltiTrade plugin = plugin("en");

        TradeConfig first = load(plugin);

        assertThat(first.getRequestTimeout()).as("an operator's value is untouched").isEqualTo(45);
        assertThat(first.getTradeTax()).isEqualTo(0.25);
        assertThat(fileOnDisk().getComments("request-timeout"))
                .containsExactly(CatalogueText.text("en", keyOf(declaredComments().get("request-timeout"))));
        assertCommentsIn("en");
        byte[] afterFirstStart = Files.readAllBytes(file().toPath());

        load(plugin);

        assertThat(Files.readAllBytes(file().toPath())).as("the second start rewrites nothing").isEqualTo(afterFirstStart);
    }
}
