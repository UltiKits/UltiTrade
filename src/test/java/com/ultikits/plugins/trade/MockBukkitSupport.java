package com.ultikits.plugins.trade;

import org.bukkit.Bukkit;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;

/**
 * Defensive MockBukkit singleton-cleanup helper for this module's tests.
 * <p>
 * Equivalent logic exists in the UltiTools framework's own test tree and in several sibling
 * modules. It is duplicated here rather than shared because each UltiKits module is a separate
 * git repository and no common test-support artifact is published to a Maven repository, so
 * there is nothing for this module to depend on.
 * <p>
 * Named {@code MockBukkitSupport}, deliberately not {@code MockBukkitHelper}: that name is
 * already taken by unrelated copies elsewhere in the UltiKits sources — the framework's own
 * helper in UltiTools-Reborn, plus separate copies in the UltiMail and UltiEssentials modules.
 * Reusing it here would leave a cross-repository search for one module's guard unable to tell it
 * apart from another's.
 */
final class MockBukkitSupport {

    private MockBukkitSupport() {
    }

    /**
     * Call before {@code MockBukkit.mock()} in {@code @BeforeEach}. Force-clears MockBukkit's and
     * Bukkit's static singleton fields, tolerating exceptions, so a prior test's failed teardown
     * cannot leave {@code MockBukkit.mock()} throwing
     * {@code IllegalStateException: Already mocking} for the next class in the same reused
     * Surefire fork.
     * <p>
     * On the normal path {@code MockBukkit.unmock()} clears both singletons itself, so the
     * reflective clear below only matters on the failure path: in MockBukkit 4.101.0
     * {@code unmock()} nulls the singletons inside a handler that covers just its
     * scheduler-shutdown step, so a throw from plugin disabling, or from the unload/reset step
     * that follows it, propagates with {@code MockBukkit.mock} still set.
     */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // test helper requires reflection for singleton cleanup
    static void ensureCleanState() {
        try {
            if (MockBukkit.isMocked()) {
                MockBukkit.unmock();
            }
        } catch (Exception ignored) {
            // best-effort cleanup only
        }

        try {
            // MockBukkit 4.101.0 declares exactly one field, "private static ServerMock mock";
            // both isMocked() and mock() gate on it being non-null.
            Field mockField = MockBukkit.class.getDeclaredField("mock");
            mockField.setAccessible(true);
            mockField.set(null, null);
        } catch (Exception ignored) {
            // best-effort cleanup only
        }

        if (Bukkit.getServer() != null) {
            try {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                serverField.set(null, null);
            } catch (Exception ignored) {
                // best-effort cleanup only
            }
        }
    }

    /**
     * Call in {@code @AfterEach}. Unmocks, tolerating exceptions, then force-clears again so the
     * next test class starts from a known-clean state regardless of how this test's teardown went.
     */
    static void safeUnmock() {
        try {
            MockBukkit.unmock();
        } catch (Exception ignored) {
            // best-effort cleanup only
        }
        ensureCleanState();
    }
}
