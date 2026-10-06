package com.ultikits.plugins.trade.testsupport;

import com.ultikits.ultitools.abstracts.data.BaseDataEntity;
import com.ultikits.ultitools.interfaces.DataOperator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/**
 * Test support: a {@link DataOperator} that runs another writer's action just before this server's
 * write reaches the database -- the window in which a second server sharing the database changes the
 * row this server has already read.
 * <p>
 * Every call is passed to the real operator. Before each call whose method name starts with
 * {@code update} ({@code update}, {@code updateCounted}, {@code updateIf}), the hook runs, for at most
 * {@code times} such calls; the hook itself writes through its own operator, never through this one.
 */
public final class InterleavingOperator {

    private InterleavingOperator() {
    }

    /** {@code operator}, with {@code hook} run before each of its first {@code times} update calls. */
    @SuppressWarnings("unchecked")
    public static <T extends BaseDataEntity<String>> DataOperator<T> beforeUpdates(
            DataOperator<T> operator, int times, Runnable hook) {
        int[] ran = {0};
        return (DataOperator<T>) Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("update") && ran[0] < times) {
                        ran[0]++;
                        hook.run();
                    }
                    try {
                        return method.invoke(operator, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** {@code operator}, with {@code hook} run once, before its first update call. */
    public static <T extends BaseDataEntity<String>> DataOperator<T> beforeFirstUpdate(DataOperator<T> operator, Runnable hook) {
        return beforeUpdates(operator, 1, hook);
    }

    /** {@code operator}, with {@code hook} run once, just before its first insert. */
    @SuppressWarnings("unchecked")
    public static <T extends BaseDataEntity<String>> DataOperator<T> beforeFirstInsert(DataOperator<T> operator, Runnable hook) {
        boolean[] ran = {false};
        return (DataOperator<T>) Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    if (method.getName().equals("insert") && !ran[0]) {
                        ran[0] = true;
                        hook.run();
                    }
                    try {
                        return method.invoke(operator, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /**
     * {@code operator}, with {@code hook} run right after the first update call made off {@code mainThread} has
     * returned -- the window between a background writer's database write and its publication to the cache.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BaseDataEntity<String>> DataOperator<T> afterFirstUpdateOffThread(
            DataOperator<T> operator, Thread mainThread, Runnable hook) {
        boolean[] ran = {false};
        return (DataOperator<T>) Proxy.newProxyInstance(
                DataOperator.class.getClassLoader(), new Class<?>[] {DataOperator.class}, (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(operator, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (method.getName().startsWith("update") && Thread.currentThread() != mainThread && !ran[0]) {
                        ran[0] = true;
                        hook.run();
                    }
                    return result;
                });
    }
}
