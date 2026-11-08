package com.ultikits.plugins.trade.util;

import java.util.concurrent.TimeUnit;

/**
 * One monotonic deadline for the settings-lock waits within a command or trade request.
 * This bounds lock waiting, not database calls or the total execution time. Nested operations share
 * the outer deadline; try-with-resources removes it even on an early return or exception.
 * A plain ThreadLocal deliberately does not pass a server-thread deadline to background work.
 * <p>
 * Internal to this module: it is {@code public} only because callers live in several packages.
 * It is not an API for other plugins and may change or move without notice (UltiKits/UltiTrade#96).
 */
public final class SettingsWaitBudget implements AutoCloseable {
    public static final long WAIT_MILLIS = 50;
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private final boolean owner;

    private SettingsWaitBudget() {
        owner = DEADLINE.get() == null;
        if (owner) {
            DEADLINE.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS));
        }
    }

    /** Begin an operation, or share the current operation's deadline when nested. */
    public static SettingsWaitBudget open() {
        return new SettingsWaitBudget();
    }

    /**
     * Remaining allowance for a main-thread acquisition. Outside an operation each call keeps its
     * original 50 ms allowance. At expiry, a zero-time attempt may still acquire an available lock.
     * Background callers of the service do not use this allowance: they wait until acquired.
     */
    public static long remainingNanos() {
        Long deadline = DEADLINE.get();
        return deadline == null ? TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS)
                : Math.max(0, deadline - System.nanoTime());
    }

    @Override
    public void close() {
        if (owner) {
            DEADLINE.remove();
        }
    }
}
