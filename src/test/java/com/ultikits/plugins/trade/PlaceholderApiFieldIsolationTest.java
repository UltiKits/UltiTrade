package com.ultikits.plugins.trade;

import org.jetbrains.annotations.NotNull;
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
 * without PlaceholderAPI on the classpath (UltiKits/UltiTrade#48).
 * <p>
 * {@link PlaceholderApiIsolationTest} guards against a scanned class directly extending a
 * PlaceholderAPI type. It does not catch the shape this issue actually had: the module main class
 * ({@link UltiTrade}) with a field whose declared type is a project-owned class
 * ({@code TradePlaceholderExpansion}) that itself extends a PlaceholderAPI type. The framework's IoC
 * container reflects over a bean's declared fields at autowire time ({@code
 * AutowireFactory#autowireBean}, {@code Class#getDeclaredFields()}), and the JVM resolves every
 * field's declared type -- and that type's own supertype chain -- as part of that call. On a server
 * without PlaceholderAPI installed this throws {@link NoClassDefFoundError} and the whole module
 * fails to load, exactly as measured in Phase 17 wave-4 gate 3: {@code Cannot initialize plugin for
 * com.ultikits.plugins.trade.UltiTrade: me/clip/placeholderapi/expansion/PlaceholderExpansion},
 * present since this module's initial commit (UltiKits/UltiEconomy#34's sibling defect).
 * <p>
 * This test reproduces the crash directly: it defines a fresh copy of each class under this
 * module's own scan prefix through a classloader that hides {@code me.clip.placeholderapi.*}
 * (everything else -- the JDK, Bukkit, Vault, this module's own supporting classes -- is served
 * normally), then calls {@code getDeclaredFields()}/{@code getDeclaredMethods()}/
 * {@code getDeclaredConstructors()} on each, the same reflection the container performs.
 */
@DisplayName("No scanned class's declared members require PlaceholderAPI to enumerate (#48)")
class PlaceholderApiFieldIsolationTest {

    private static final String SCAN_PREFIX = "com/ultikits/plugins/trade";
    private static final String HIDDEN_PREFIX = "me.clip.placeholderapi.";
    private static final String OWN_PREFIX = "com.ultikits.plugins.";

    /**
     * A fixture standing in for {@code TradePlaceholderExpansion}: a project-owned class whose
     * superclass is a real PlaceholderAPI type. Loading this class at all -- not just instantiating
     * it -- requires the JVM to resolve {@link me.clip.placeholderapi.expansion.PlaceholderExpansion},
     * because loading a class requires its direct superclass to already be resolvable.
     */
    static class HiddenSupertypeFixture extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
        @Override
        public @NotNull String getIdentifier() {
            return "fixture";
        }

        @Override
        public @NotNull String getAuthor() {
            return "fixture";
        }

        @Override
        public @NotNull String getVersion() {
            return "1.0";
        }
    }

    /**
     * A fixture standing in for {@link UltiTrade} before this issue's fix: a field typed as the
     * project-owned wrapper, not the PlaceholderAPI type directly -- exactly the shape that slipped
     * past {@link PlaceholderApiIsolationTest}'s supertype-only check.
     */
    @SuppressWarnings("unused")
    static class FixtureWithHiddenFieldType {
        private HiddenSupertypeFixture wrapped;
    }

    @Test
    @DisplayName("control: a field typed as a wrapper over a PlaceholderAPI type is caught")
    void controlFixtureReproducesTheCrash() throws Exception {
        List<String> failures = enumerationFailures(FixtureWithHiddenFieldType.class.getName());
        assertThat(failures)
                .as("the hiding classloader must actually reproduce NoClassDefFoundError for a known-bad "
                        + "field type, or this test proves nothing about the real check below")
                .isNotEmpty();
    }

    @Test
    @DisplayName("every class under com/ultikits/plugins/trade enumerates cleanly without PlaceholderAPI")
    void everyScannedClassEnumeratesWithoutPlaceholderApi() throws Exception {
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

    /**
     * Loads {@code className} fresh through a classloader that hides {@code me.clip.placeholderapi.*}
     * and child-loads everything under {@value #OWN_PREFIX} from this module's own build output
     * (so a project-owned class's supertype resolution goes through the same hiding loader instead
     * of falling back to a parent that can still see PlaceholderAPI), then attempts to enumerate its
     * declared fields, methods and constructors -- returning one failure message per member
     * category that could not even be enumerated, rather than letting the {@link LinkageError}
     * escape and fail the whole run with no context.
     */
    private static List<String> enumerationFailures(String className) throws Exception {
        PapiHidingClassLoader loader = new PapiHidingClassLoader(
                PlaceholderApiFieldIsolationTest.class.getClassLoader());
        Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            // On some JVMs, merely defining a class already resolves a poisoned field's type
            // (rather than deferring that to getDeclaredFields() below) -- either point is a
            // faithful reproduction of the real crash, so both are reported the same way.
            return java.util.Collections.singletonList(className + " could not even be loaded: " + e);
        }
        assertThat(type.getClassLoader())
                .as("the class under test must be defined fresh by the hiding loader, not answered by a "
                        + "parent that can already see PlaceholderAPI, or this test proves nothing")
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

    /**
     * Hides {@code me.clip.placeholderapi.*} entirely (as if PlaceholderAPI were not installed) and
     * defines every class under {@value #OWN_PREFIX} fresh from this module's own build output,
     * rather than delegating to the parent classloader -- which, under this module's {@code test}
     * scope, still has PlaceholderAPI on its classpath ({@code provided} scope) and would silently
     * resolve the very type this loader exists to hide.
     */
    private static final class PapiHidingClassLoader extends ClassLoader {

        PapiHidingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(HIDDEN_PREFIX)) {
                throw new ClassNotFoundException(
                        "PlaceholderAPI is hidden by " + getClass().getSimpleName() + ": " + name);
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
