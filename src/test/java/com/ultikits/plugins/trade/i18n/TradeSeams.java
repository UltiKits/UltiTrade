package com.ultikits.plugins.trade.i18n;

import com.ultikits.plugins.trade.config.RemovedConfigKeys;
import com.ultikits.plugins.trade.service.TradeService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Test support for the language sweep's seams.
 * <p>
 * Routing this module's text through its language file gives {@link TradeService} an {@code i18n}
 * pass-through (the GUIs, the listener and the placeholder expansion reach the catalogue through the
 * service they already hold) and {@link RemovedConfigKeys#warnAboutLeftovers} a plugin parameter. The
 * tests are written once and must compile and run against the code before and after that change (a
 * revert proof restores the old production files and runs these same tests), so they reach both
 * reflectively: when the new member exists it is stubbed or called, otherwise the old behaviour runs
 * untouched.
 */
public final class TradeSeams {

    private TradeSeams() {
    }

    /** Makes a mocked {@link TradeService} answer {@code i18n} from {@code code}'s catalogue, when it has one. */
    public static void speak(TradeService mockService, String code) {
        Method i18n;
        try {
            i18n = TradeService.class.getMethod("i18n", String.class);
        } catch (NoSuchMethodException e) {
            return;
        }
        try {
            lenient().when(i18n.invoke(mockService, anyString())).thenAnswer(CatalogueText.answer(code));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot stub TradeService#i18n", e);
        }
    }

    /** {@link RemovedConfigKeys#warnAboutLeftovers}, given the plugin when the method takes one. */
    public static void warnAboutLeftovers(File configFile, Consumer<String> warn, UltiToolsPlugin plugin) {
        try {
            try {
                RemovedConfigKeys.class.getMethod("warnAboutLeftovers", File.class, Consumer.class, UltiToolsPlugin.class)
                        .invoke(null, configFile, warn, plugin);
            } catch (NoSuchMethodException e) {
                RemovedConfigKeys.class.getMethod("warnAboutLeftovers", File.class, Consumer.class)
                        .invoke(null, configFile, warn);
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot call RemovedConfigKeys#warnAboutLeftovers", e);
        }
    }

    /** Binds a real configuration object to {@code plugin}, as the framework's {@code init} does. */
    public static void bind(com.ultikits.ultitools.abstracts.AbstractConfigEntity config, UltiToolsPlugin plugin) {
        try {
            java.lang.reflect.Field field =
                    com.ultikits.ultitools.abstracts.AbstractConfigEntity.class.getDeclaredField("ultiToolsPlugin");
            field.setAccessible(true);
            field.set(config, plugin);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot bind the configuration to its plugin", e);
        }
    }
}
