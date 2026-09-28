package com.ultikits.plugins.trade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without PlaceholderAPI the module loads with no class error (UltiKits/UltiTrade#48).
 * <p>
 * At load the framework's component scan loads every class whose jar entry name starts with the
 * module's scan package path, {@code com/ultikits/plugins/trade} -- a plain string prefix, as
 * {@code ComponentScanner#scanJar} matches it. A class there that extends a PlaceholderAPI type
 * fails to load on a server without PlaceholderAPI and is logged as a skipped class. So no such
 * class may carry a PlaceholderAPI supertype; the expansion lives outside that prefix (moved from
 * {@code com.ultikits.plugins.trade.placeholder} to {@code com.ultikits.plugins.placeholderapi.trade}
 * by this fix) and is created only after the module has checked that PlaceholderAPI is installed.
 */
@DisplayName("No class the framework scans at load extends a PlaceholderAPI type (#48)")
class PlaceholderApiIsolationTest {

    private static final String SCAN_PREFIX = "com/ultikits/plugins/trade";

    @Test
    @DisplayName("Every class under the scanned prefix loads without a PlaceholderAPI supertype")
    void noScannedClassHasAPlaceholderApiSupertype() throws Exception {
        Path classes = Paths.get("target", "classes");
        List<String> scanned;
        try (Stream<Path> files = Files.walk(classes)) {
            scanned = files.map(p -> classes.relativize(p).toString().replace('\\', '/'))
                    .filter(name -> name.startsWith(SCAN_PREFIX) && name.endsWith(".class"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .collect(Collectors.toList());
        }
        assertThat(scanned).as("control: the scan reached the module's classes").contains(UltiTrade.class.getName());

        List<String> offenders = new ArrayList<>();
        for (String name : scanned) {
            for (Class<?> type = Class.forName(name, false, getClass().getClassLoader()); type != null;
                    type = type.getSuperclass()) {
                if (type.getName().startsWith("me.clip.placeholderapi.")) {
                    offenders.add(name + " extends " + type.getName());
                }
            }
        }
        assertThat(offenders).isEmpty();
    }
}
