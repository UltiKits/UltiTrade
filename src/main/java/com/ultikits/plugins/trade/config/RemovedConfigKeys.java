package com.ultikits.plugins.trade.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reports configuration keys this module no longer reads but which are still sitting in the
 * operator's own {@code config/trade.yml}.
 * <p>
 * Deleting a key from {@link TradeConfig} stops the framework writing it into a fresh file, but it
 * does nothing to the files already on disk: the framework only ever writes a declared default for a
 * key that is <em>missing</em>, so an existing install keeps the key, keeps whatever value the
 * operator gave it, and gets no indication that the value means nothing. This class is that
 * indication -- one warning per leftover key that still has a value (see
 * {@link #warnAboutLeftovers}), naming the module, the file and the key, and saying where the
 * setting went.
 *
 * @author wisdomme
 * @version 1.0.0
 */
public final class RemovedConfigKeys {

    /**
     * Every key removed from {@code config/trade.yml}, mapped to what an operator should be told about
     * it. Insertion order is the order the warnings are emitted in.
     */
    private static final Map<String, String> REMOVED;

    static {
        Map<String, String> removed = new LinkedHashMap<String, String>();
        removed.put("trade-timeout",
                "It never took effect: an open trade window has no time limit, and nothing replaces "
                        + "the setting. A pending trade request still expires after 'request-timeout', "
                        + "which is unchanged. A time limit for an open trade window is feature request "
                        + "UltiKits/UltiTrade#41 (UltiKits/UltiTrade#18).");
        removed.put("messages.toggle-on", movedToCatalogue("trade_toggle_on"));
        removed.put("messages.toggle-off", movedToCatalogue("trade_toggle_off"));
        removed.put("messages.block-success", movedToCatalogue("block_success"));
        removed.put("messages.unblock-success", movedToCatalogue("unblock_success"));
        removed.put("messages.already-blocked", movedToCatalogue("already_blocked"));
        removed.put("messages.not-blocked", movedToCatalogue("not_blocked"));
        REMOVED = Collections.unmodifiableMap(removed);
    }

    private RemovedConfigKeys() {
        // Utility class
    }

    private static String movedToCatalogue(String catalogueKey) {
        return "Its value was never shown to players. The reply it described now comes from the '"
                + catalogueKey + "' entry of this module's language file (lang/<language>.yml, in the "
                + "same module folder as config/trade.yml), so it follows the server's language "
                + "setting; edit it there (UltiKits/UltiTrade#17).";
    }

    /**
     * The keys this class knows about, in the order it reports them.
     *
     * @return an unmodifiable map of removed key path to the guidance printed for it
     */
    public static Map<String, String> removedKeys() {
        return REMOVED;
    }

    /**
     * Emit one warning per removed key that is still present, with a value, in the operator's
     * configuration file.
     * <p>
     * A key left with no value ({@code trade-timeout:} or {@code trade-timeout: ~}) is not reported:
     * Bukkit's YAML loader drops a null-valued key, so it reads back exactly as if it were absent. It
     * carries no setting for the operator to lose, and the framework never writes one.
     * <p>
     * Silent when the file is absent or unreadable -- there is then nothing to report and nothing to
     * be sure of. A parse failure is deliberately not reported here: the framework's own config
     * loading already fails loudly on an unparseable file, and a second message from this check would
     * only add noise to it.
     *
     * @param configFile the operator's {@code config/trade.yml}; may be {@code null}
     * @param warn       where to send each warning, normally the module logger's warn method
     */
    public static void warnAboutLeftovers(File configFile, Consumer<String> warn) {
        if (configFile == null || !configFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            return;
        }
        for (Map.Entry<String, String> entry : REMOVED.entrySet()) {
            if (yaml.contains(entry.getKey())) {
                // No "[UltiTrade]" prefix: the module logger adds that itself, and the module is
                // still named in the sentence for any consumer that does not.
                warn.accept(configFile.getPath() + " still contains '"
                        + entry.getKey() + "', which this version of UltiTrade no longer reads. "
                        + entry.getValue()
                        + " Delete the key from the file to silence this warning.");
            }
        }
    }
}
