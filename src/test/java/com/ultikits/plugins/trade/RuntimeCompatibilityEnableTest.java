package com.ultikits.plugins.trade;

import com.ultikits.plugins.trade.i18n.CatalogueText;
import com.ultikits.plugins.trade.service.TradeLogService;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RuntimeCompatibilityEnableTest {
    @org.junit.jupiter.api.Test
    void oldApiScanAndFrameworkRefusalLeaveModuleDisabled() throws Exception {
        // Simulate the old API's CLASS shape without making tests depend on a local server JAR.
        byte[] oldView = new net.bytebuddy.ByteBuddy().subclass(Object.class)
                .name("org.bukkit.inventory.InventoryView").make().getBytes();
        java.net.URL classes = UltiTrade.class.getProtectionDomain().getCodeSource().getLocation();
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{classes}, getClass().getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                Class<?> type = findLoadedClass(name);
                if (type == null && name.equals("org.bukkit.inventory.InventoryView")) {
                    type = defineClass(name, oldView, 0, oldView.length);
                }
                if (type == null && name.startsWith("com.ultikits.plugins.")) {
                    type = findClass(name);
                }
                if (type == null) {
                    type = super.loadClass(name, false);
                }
                if (resolve) { resolveClass(type); }
                return type;
            }

            @Override
            public java.net.URL getResource(String name) {
                return name.startsWith("com/ultikits/plugins/") ? findResource(name) : super.getResource(name);
            }
        }) {
            assertThat(loader.loadClass("org.bukkit.inventory.InventoryView").isInterface()).isFalse();
            SimpleContainer scanned = new SimpleContainer();
            scanned.setClassLoader(loader);
            scanned.scanComponents("com.ultikits.plugins.trade");
            // A nonempty positive control prevents a silently skipped scan from satisfying this test.
            assertThat(scanned.getBeanDefinitionNames()).contains("tradeService", "tradeLogService",
                    "tradeListener", "tradeSettingsCacheListener", "tradeCommand");
            java.nio.file.Path root = java.nio.file.Paths.get(classes.toURI());
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
                java.util.List<java.nio.file.Path> all = files.filter(p -> p.toString().endsWith(".class"))
                        .collect(java.util.stream.Collectors.toList());
                assertThat(all.size()).isGreaterThan(20);
                for (java.nio.file.Path file : all) {
                    String name = root.relativize(file).toString().replace(java.io.File.separatorChar, '.');
                    Class<?> type = Class.forName(name.substring(0, name.length() - 6), true, loader);
                    for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                        for (java.lang.annotation.Annotation annotation : method.getDeclaredAnnotations()) {
                            assertThat(annotation.annotationType().getSimpleName()).isNotEqualTo("PostConstruct");
                        }
                    }
                }
            }
            // These are all discovered module bean constructors; none executes a GUI call site.
            for (String bean : new String[]{"service.TradeService", "service.TradeLogService",
                    "listener.TradeListener", "listener.TradeSettingsCacheListener", "config.TradeConfig"}) {
                assertThat(loader.loadClass("com.ultikits.plugins.trade." + bean)
                        .getDeclaredConstructor().newInstance()).isNotNull();
            }
            Class<?> trades = loader.loadClass("com.ultikits.plugins.trade.service.TradeService");
            Class<?> logs = loader.loadClass("com.ultikits.plugins.trade.service.TradeLogService");
            loader.loadClass("com.ultikits.plugins.trade.commands.TradeCommand")
                    .getConstructor(com.ultikits.ultitools.abstracts.UltiToolsPlugin.class, trades, logs)
                    .newInstance(null, trades.getDeclaredConstructor().newInstance(), logs.getDeclaredConstructor().newInstance());

            // Exercise the published 6.3.0 manager's actual activation/refusal branch, not a copy of it.
            com.ultikits.ultitools.manager.PluginManager framework = new com.ultikits.ultitools.manager.PluginManager();
            com.ultikits.ultitools.manager.TaskManager tasks = mock(com.ultikits.ultitools.manager.TaskManager.class);
            java.lang.reflect.Field tasksField = framework.getClass().getDeclaredField("taskManager");
            tasksField.setAccessible(true);
            tasksField.set(framework, tasks);
            com.ultikits.ultitools.abstracts.UltiToolsPlugin module =
                    (com.ultikits.ultitools.abstracts.UltiToolsPlugin) mock(loader.loadClass(UltiTrade.class.getName()));
            SimpleContainer context = mock(SimpleContainer.class);
            PluginLogger logger = mock(PluginLogger.class);
            when(module.getContext()).thenReturn(context);
            when(module.getPluginName()).thenReturn("UltiTrade");
            when(module.getVersion()).thenReturn("2.0.0");
            when(module.getLogger()).thenReturn(logger);
            when(module.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
            when(module.registerSelf()).thenCallRealMethod();
            PluginManager bukkitManager = mock(PluginManager.class);
            Plugin core = mock(Plugin.class);
            when(bukkitManager.getPlugin("UltiTools")).thenReturn(core);
            when(core.getDescription()).thenReturn(new PluginDescriptionFile("UltiTools", "6.3.0", "unused"));
            // The core's JavaPlugin constructor must not run in a unit test; allocate only a blank
            // fixture so its null optional managers take the real framework's guarded paths.
            java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            com.ultikits.ultitools.UltiTools blankCore = (com.ultikits.ultitools.UltiTools)
                    ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(com.ultikits.ultitools.UltiTools.class);
            java.util.logging.Logger frameworkLog = mock(java.util.logging.Logger.class);
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
                 MockedStatic<com.ultikits.ultitools.UltiTools> singleton = mockStatic(com.ultikits.ultitools.UltiTools.class)) {
                singleton.when(com.ultikits.ultitools.UltiTools::getInstance).thenReturn(blankCore);
                bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.20.6-R0.1-SNAPSHOT");
                bukkit.when(Bukkit::getPluginManager).thenReturn(bukkitManager);
                bukkit.when(Bukkit::getLogger).thenReturn(frameworkLog);
                java.lang.reflect.Method activate = framework.getClass().getDeclaredMethod("attemptPluginRegistration",
                        com.ultikits.ultitools.abstracts.UltiToolsPlugin.class);
                activate.setAccessible(true);
                assertThat(activate.invoke(framework, module)).isEqualTo(false);
                assertThat(framework.getPluginList()).isEmpty();
                verify(context).close();
                verifyNoMoreInteractions(context); // no bean enumeration for listeners, commands or GUI entry points
                verifyNoInteractions(tasks); // no @Scheduled registrations
                verify(module).registerSelf();
                verify(logger).error(CatalogueText.entries("en").get("startup_framework_too_old")
                        .replace("{SERVER}", "1.20.6-R0.1-SNAPSHOT").replace("{FRAMEWORK}", "6.3.0"));
                verifyNoMoreInteractions(logger);
                verify(frameworkLog).log(java.util.logging.Level.WARNING,
                        "[UltiTools-API] UltiTrade load failed! Version: 2.0.0.");
                verifyNoMoreInteractions(frameworkLog); // excludes the manager's exception/failure fallback
                verify(bukkitManager).getPlugin("UltiTools");
                verifyNoMoreInteractions(bukkitManager); // no Bukkit listeners or plugin disable
                bukkit.verify(Bukkit::getBukkitVersion);
                bukkit.verify(Bukkit::getPluginManager);
                bukkit.verify(Bukkit::getLogger);
                bukkit.verifyNoMoreInteractions(); // no command map, scheduler, inventory/GUI registration
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"en, 6.3.0", "zh, 6.3.1-SNAPSHOT"})
    void refusesBeforeAnyModuleWorkOrRegistration(String language, String frameworkVersion) {
        PluginManager manager = mock(PluginManager.class);
        Plugin core = mock(Plugin.class);
        when(manager.getPlugin("UltiTools")).thenReturn(core);
        when(core.getDescription()).thenReturn(new PluginDescriptionFile("UltiTools", frameworkVersion, "unused"));
        UltiTrade module = mock(UltiTrade.class);
        PluginLogger logger = mock(PluginLogger.class);
        when(module.getLogger()).thenReturn(logger);
        when(module.i18n(anyString())).thenAnswer(CatalogueText.answer(language));
        when(module.registerSelf()).thenCallRealMethod();
        when(module.getContext()).thenReturn(mock(SimpleContainer.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.20.6-R0.1-SNAPSHOT");
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);

            assertThat(module.registerSelf()).isFalse();

            String expected = CatalogueText.entries(language).get("startup_framework_too_old")
                    .replace("{SERVER}", "1.20.6-R0.1-SNAPSHOT").replace("{FRAMEWORK}", frameworkVersion);
            verify(logger).error(expected);
            assertThat(expected).contains("6.4.0", "1.20.6-R0.1-SNAPSHOT", frameworkVersion);
            verifyNoMoreInteractions(logger);
            verify(module, never()).getContext();
            verify(module, never()).getConfig(any());
            verify(module, never()).operatorConfigFile();
            verify(manager).getPlugin("UltiTools");
            verifyNoMoreInteractions(manager);
            bukkit.verify(Bukkit::getBukkitVersion);
            bukkit.verify(Bukkit::getPluginManager);
            bukkit.verifyNoMoreInteractions(); // no scheduler, event/command registration or core disable
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1.20.6, 6.4.0, false",
            "1.20.6, 6.4.0-SNAPSHOT, false",
            "1.21, 6.3.0, false",
            "1.21.11-R0.1-SNAPSHOT, 6.3.0, false",
            "garbage, 6.3.0, true",
            "1.20.6, garbage, true",
            "garbage, garbage, true",
            "1.20.6, , true"
    })
    void continuesAndWarnsOnlyForUnknownVersions(String serverVersion, String frameworkVersion, boolean unknown) {
        PluginManager manager = mock(PluginManager.class);
        Plugin core = mock(Plugin.class);
        when(manager.getPlugin("UltiTools")).thenReturn(core);
        // A missing core is treated just like an unparseable version, not a guessed refusal.
        if (frameworkVersion == null) {
            when(manager.getPlugin("UltiTools")).thenReturn(null);
        } else {
            when(core.getDescription()).thenReturn(new PluginDescriptionFile("UltiTools", frameworkVersion, "unused"));
        }
        UltiTrade module = mock(UltiTrade.class);
        PluginLogger logger = mock(PluginLogger.class);
        SimpleContainer context = mock(SimpleContainer.class);
        TradeService trades = mock(TradeService.class);
        TradeLogService logs = mock(TradeLogService.class);
        when(module.getLogger()).thenReturn(logger);
        when(module.getContext()).thenReturn(context);
        when(context.getBean(TradeService.class)).thenReturn(trades);
        when(context.getBean(TradeLogService.class)).thenReturn(logs);
        when(module.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        when(module.registerSelf()).thenCallRealMethod();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getBukkitVersion).thenReturn(serverVersion);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);

            assertThat(module.registerSelf()).isTrue();

            verify(trades).init();
            verify(logs).init();
            verify(logger).info("UltiTrade enabled!");
            if (unknown) {
                verify(logger).warn(CatalogueText.entries("en").get("startup_version_unknown")
                        .replace("{SERVER}", serverVersion).replace("{FRAMEWORK}", String.valueOf(frameworkVersion)));
            } else {
                verify(logger, never()).warn(anyString());
            }
            verify(logger, never()).error(anyString());
        }
    }
}
