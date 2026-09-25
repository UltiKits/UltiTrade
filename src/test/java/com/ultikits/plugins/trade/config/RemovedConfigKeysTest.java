package com.ultikits.plugins.trade.config;

import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.i18n.TradeSeams;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UltiKits/UltiTrade#17 and #18. Deleting a key from {@link TradeConfig} stops the framework writing
 * it into a fresh {@code trade.yml} and does nothing to the files already on disk: the framework
 * writes a declared default only for a key that is missing and never removes one, so every upgraded
 * server keeps all seven removed keys, with whatever values its operator gave them. This check is the
 * only thing that tells that operator the values mean nothing.
 * <p>
 * The positive controls come first on purpose: a check that never fires and a server with no leftover
 * key print the same empty console, so the negative cases below prove nothing on their own.
 */
@DisplayName("RemovedConfigKeys (UltiKits/UltiTrade#17, #18)")
class RemovedConfigKeysTest {

    /**
     * The module, answering {@code i18n} from its English catalogue: the assertions below quote the
     * English guidance an operator reads under {@code language: en} (UltiKits/UltiTrade#16).
     */
    private static final UltiToolsPlugin ENGLISH = englishPlugin();

    private static UltiToolsPlugin englishPlugin() {
        UltiToolsPlugin plugin = org.mockito.Mockito.mock(UltiToolsPlugin.class);
        org.mockito.Mockito.when(plugin.i18n(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(CatalogueText.answer("en"));
        return plugin;
    }


    /**
     * The shape the framework wrote on the shared UAT server before this change: the seven removed
     * keys sit among keys that are still read, including the two whose names they most resemble
     * ({@code request-timeout}, {@code messages.request-timeout}).
     */
    private static final String FILE_WITH_EVERY_REMOVED_KEY =
            "request-timeout: 30\n"
            + "trade-timeout: 120\n"
            + "max-distance: 50\n"
            + "messages:\n"
            + "  request-sent: '&asent {PLAYER}'\n"
            + "  request-timeout: '&ctimed out'\n"
            + "  player-blocked: '&cblocked'\n"
            + "  toggle-on: '&a你已开启交易功能！'\n"
            + "  toggle-off: '&c你已关闭交易功能！'\n"
            + "  block-success: '&a已将 {PLAYER} 加入交易黑名单！'\n"
            + "  unblock-success: '&a已将 {PLAYER} 移出交易黑名单！'\n"
            + "  already-blocked: '&c{PLAYER} 已在你的黑名单中！'\n"
            + "  not-blocked: '&c{PLAYER} 不在你的黑名单中！'\n";

    /** The same file with only the seven removed keys taken out. */
    private static final String FILE_WITHOUT_ANY_REMOVED_KEY =
            "request-timeout: 30\n"
            + "max-distance: 50\n"
            + "messages:\n"
            + "  request-sent: '&asent {PLAYER}'\n"
            + "  request-timeout: '&ctimed out'\n"
            + "  player-blocked: '&cblocked'\n";

    private static File write(File dir, String body) throws IOException {
        File file = new File(dir, "trade.yml");
        Files.write(file.toPath(), body.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    @DisplayName("POSITIVE CONTROL: all seven leftover keys produce seven warnings, in the check's order, each naming the module, the file and its key")
    void warnsAboutEveryLeftoverKey(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITH_EVERY_REMOVED_KEY);
        List<String> warnings = new ArrayList<>();

        TradeSeams.warnAboutLeftovers(file, warnings::add, ENGLISH);

        assertThat(warnings).hasSize(7);
        List<String> keys = new ArrayList<>(RemovedConfigKeys.removedKeys().keySet());
        for (int i = 0; i < warnings.size(); i++) {
            assertThat(warnings.get(i))
                    .contains("UltiTrade")
                    .contains(file.getPath())
                    .contains("'" + keys.get(i) + "'")
                    .contains("Delete the key");
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "trade-timeout,             UltiKits/UltiTrade#41",
        "messages.toggle-on,        trade_toggle_on",
        "messages.toggle-off,       trade_toggle_off",
        "messages.block-success,    block_success",
        "messages.unblock-success,  unblock_success",
        "messages.already-blocked,  already_blocked",
        "messages.not-blocked,      not_blocked",
    })
    @DisplayName("POSITIVE CONTROL: each leftover key alone produces exactly one warning, saying where the setting went")
    void warnsAboutEachLeftoverKeyAlone(String key, String whereItWent, @TempDir File dir) throws IOException {
        // Built by adding the one key to the clean file, so nothing else in it can produce the warning.
        String body = key.startsWith("messages.")
                ? FILE_WITHOUT_ANY_REMOVED_KEY + "  " + key.substring("messages.".length()) + ": 'x'\n"
                : key + ": 120\n" + FILE_WITHOUT_ANY_REMOVED_KEY;
        File file = write(dir, body);
        List<String> warnings = new ArrayList<>();

        TradeSeams.warnAboutLeftovers(file, warnings::add, ENGLISH);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("UltiTrade")
                .contains(file.getPath())
                .contains("'" + key + "'")
                .contains(whereItWent);
        if (key.startsWith("messages.")) {
            assertThat(warnings.get(0)).contains("lang/").contains("UltiKits/UltiTrade#17");
        } else {
            assertThat(warnings.get(0)).contains("request-timeout").contains("UltiKits/UltiTrade#18");
        }
    }

    @Test
    @DisplayName("an empty leftover value is still a leftover key")
    void warnsAboutAnEmptyLeftoverValue(@TempDir File dir) throws IOException {
        File file = write(dir, "messages:\n  toggle-on: ''\n");
        List<String> warnings = new ArrayList<>();

        TradeSeams.warnAboutLeftovers(file, warnings::add, ENGLISH);

        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("no warning for the same file with only the removed keys taken out, whose read neighbours share their names")
    void silentWithoutTheKeys(@TempDir File dir) throws IOException {
        File file = write(dir, FILE_WITHOUT_ANY_REMOVED_KEY);
        List<String> warnings = new ArrayList<>();

        TradeSeams.warnAboutLeftovers(file, warnings::add, ENGLISH);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("no warning, and no exception, for a missing file, no file at all, or a file that does not parse")
    void silentWithoutAReadableFile(@TempDir File dir) throws IOException {
        List<String> warnings = new ArrayList<>();

        TradeSeams.warnAboutLeftovers(new File(dir, "absent.yml"), warnings::add, ENGLISH);
        TradeSeams.warnAboutLeftovers(null, warnings::add, ENGLISH);
        TradeSeams.warnAboutLeftovers(write(dir, "trade-timeout: [unclosed\n"), warnings::add, ENGLISH);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("the check knows exactly the seven removed keys, none of which TradeConfig still declares")
    void knowsExactlyTheRemovedKeys() {
        List<String> declared = new ArrayList<>();
        for (Field field : TradeConfig.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry != null) {
                declared.add(entry.path());
            }
        }

        assertThat(declared).as("control: the scan sees TradeConfig's keys").contains("request-timeout");
        assertThat(RemovedConfigKeys.removedKeys().keySet())
                .containsExactly(
                        "trade-timeout",
                        "messages.toggle-on",
                        "messages.toggle-off",
                        "messages.block-success",
                        "messages.unblock-success",
                        "messages.already-blocked",
                        "messages.not-blocked")
                .doesNotContainAnyElementsOf(declared);
    }
}
