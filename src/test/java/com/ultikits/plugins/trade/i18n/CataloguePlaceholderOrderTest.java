package com.ultikits.plugins.trade.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every language entry names its placeholders in the same order in both catalogues.
 * <p>
 * The module fills placeholders by name, so a different order renders correctly today; the order is
 * held equal so that a translator reading one catalogue against the other can match the sentences,
 * and so that no later change to positional formatting can swap two values silently. Found on two
 * entries added for UltiKits/UltiTrade#32 while reviewing that fix.
 */
@DisplayName("Placeholders appear in the same order in lang/en.yml and lang/zh.yml")
class CataloguePlaceholderOrderTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[A-Z_]+}");

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    @Test
    @DisplayName("every key present in both catalogues lists the same placeholders in the same order")
    void sameOrderInBothLanguages() {
        Map<String, String> en = CatalogueText.entries("en");
        Map<String, String> zh = CatalogueText.entries("zh");
        Map<String, String> mismatches = new TreeMap<>();
        int withPlaceholders = 0;
        for (Map.Entry<String, String> entry : en.entrySet()) {
            String other = zh.get(entry.getKey());
            if (other == null) {
                continue;
            }
            List<String> left = placeholders(entry.getValue());
            List<String> right = placeholders(other);
            if (!left.isEmpty()) {
                withPlaceholders++;
            }
            if (!left.equals(right)) {
                mismatches.put(entry.getKey(), left + " vs " + right);
            }
        }
        assertThat(withPlaceholders).as("control: the scan reads entries that carry placeholders").isGreaterThan(10);
        assertThat(mismatches).as("entries whose placeholders differ in order between en and zh").isEmpty();
    }
}
