package com.ultikits.plugins.trade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every class the framework reflects over enumerates its declared fields, methods and constructors
 * without Vault on the classpath.
 * <p>
 * Found by the sweep this module's PlaceholderAPI fix was accompanied by: at the time, this module's
 * {@code plugin.yml} declared NEITHER {@code depend:} NOR {@code softdepend:} for Vault (unlike
 * UltiEconomy), so nothing guaranteed Vault is present when the container autowires this module's
 * beans. {@code plugin.yml} now declares {@code softdepend: [ PlaceholderAPI, Vault ]}, both truthfully
 * -- {@code TradePlaceholderExpansion} and this class's own {@code Economy} usage are real, optional
 * integrations -- but that declaration only changes how the framework LOGS a module load failure caused
 * by a declared-optional dependency being absent (debug instead of SEVERE, per the framework's own
 * 2026-09-29 change); it does not by itself stop the reflection this test guards against. {@code
 * TradeService} (a {@code @Service} bean the container both autowires and scans for {@code @Scheduled}
 * methods -- {@code TradeService#cleanupExpiredRequests} carries one) carried a field and a method
 * return type of {@code net.milkbowl.vault.economy.Economy} directly, so a server without Vault
 * installed would throw {@link NoClassDefFoundError} the instant the container reflects over this bean
 * -- the same crash shape as the PlaceholderAPI defect (UltiKits/UltiTrade#48), just for a different
 * soft dependency, and previously unobserved because every gate-3 boot so far has had Vault installed.
 * <p>
 * This test reproduces the crash directly: it defines a fresh copy of each class under this
 * module's own scan prefix through a classloader that hides {@code net.milkbowl.vault.*}, then
 * calls {@code getDeclaredFields()}/{@code getDeclaredMethods()}/{@code getDeclaredConstructors()}
 * on each, the same reflection the container performs.
 */
@DisplayName("No scanned class's declared members require Vault to enumerate")
class VaultFieldIsolationTest {

    private static final String SCAN_PREFIX = "com/ultikits/plugins/trade";
    private static final String HIDDEN_PREFIX = "net.milkbowl.vault.";
    private static final String OWN_PREFIX = "com.ultikits.plugins.";

    /**
     * A fixture standing in for {@code TradeService} before this issue's fix: a field typed
     * directly as a Vault interface.
     */
    @SuppressWarnings("unused")
    static class FixtureWithHiddenFieldType {
        private net.milkbowl.vault.economy.Economy economy;
    }

    @Test
    @DisplayName("control: a field typed directly as a Vault type is caught")
    void controlFixtureReproducesTheCrash() throws Exception {
        List<String> failures = enumerationFailures(FixtureWithHiddenFieldType.class.getName());
        assertThat(failures)
                .as("the hiding classloader must actually reproduce NoClassDefFoundError for a known-bad "
                        + "field type, or this test proves nothing about the real check below")
                .isNotEmpty();
    }

    @Test
    @DisplayName("every class under com/ultikits/plugins/trade enumerates cleanly without Vault")
    void everyScannedClassEnumeratesWithoutVault() throws Exception {
        Path classes = Paths.get("target", "classes");
        List<String> scanned;
        try (Stream<Path> files = Files.walk(classes)) {
            scanned = files.map(p -> classes.relativize(p).toString().replace('\\', '/'))
                    .filter(name -> name.startsWith(SCAN_PREFIX) && name.endsWith(".class"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .collect(Collectors.toList());
        }
        assertThat(scanned).as("control: the scan reached the module's classes").contains(UltiTrade.class.getName());

        List<String> allFailures = new ArrayList<>();
        for (String name : scanned) {
            allFailures.addAll(enumerationFailures(name));
        }
        assertThat(allFailures).isEmpty();
    }

    private static List<String> enumerationFailures(String className) throws Exception {
        VaultHidingClassLoader loader = new VaultHidingClassLoader(
                VaultFieldIsolationTest.class.getClassLoader());
        Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return java.util.Collections.singletonList(className + " could not even be loaded: " + e);
        }
        assertThat(type.getClassLoader())
                .as("the class under test must be defined fresh by the hiding loader, not answered by a "
                        + "parent that can already see Vault, or this test proves nothing")
                .isSameAs(loader);

        List<String> failures = new ArrayList<>();
        try {
            type.getDeclaredFields();
        } catch (LinkageError e) {
            failures.add(className + "'s declared fields: " + e);
        }
        try {
            type.getDeclaredMethods();
        } catch (LinkageError e) {
            failures.add(className + "'s declared methods: " + e);
        }
        try {
            type.getDeclaredConstructors();
        } catch (LinkageError e) {
            failures.add(className + "'s declared constructors: " + e);
        }
        return failures;
    }

    private static final class VaultHidingClassLoader extends ClassLoader {

        VaultHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(HIDDEN_PREFIX)) {
                throw new ClassNotFoundException(
                        "Vault is hidden by " + getClass().getSimpleName() + ": " + name);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    if (name.startsWith(OWN_PREFIX)) {
                        loaded = findClass(name);
                    } else {
                        loaded = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            String resourcePath = name.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = readAllBytes(in);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        // Java 8 bytecode target -- InputStream#readAllBytes is a Java 9+ API.
        private static byte[] readAllBytes(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }
}
