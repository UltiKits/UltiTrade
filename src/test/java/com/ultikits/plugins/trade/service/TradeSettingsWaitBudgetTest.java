package com.ultikits.plugins.trade.service;

import com.ultikits.plugins.trade.UltiTradeTestHelper;
import com.ultikits.plugins.trade.commands.TradeCommand;
import com.ultikits.plugins.trade.config.TradeConfig;
import com.ultikits.plugins.trade.entity.PlayerTradeSettings;
import com.ultikits.plugins.trade.testsupport.SharedSqliteDatabase;
import com.ultikits.plugins.trade.util.SettingsWaitBudget;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Measures the time allowances passed to the locks, not a fragile wall-clock upper bound. */
class TradeSettingsWaitBudgetTest {
    @TempDir
    Path dir;

    @BeforeEach
    void setUp() throws Exception {
        UltiTradeTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        UltiTradeTestHelper.tearDown();
    }

    @ParameterizedTest
    @ValueSource(strings = {"block", "unblock", "request"})
    void oneWaitBudgetAcrossAllSettingsChecks(String operation) throws Exception {
        SharedSqliteDatabase database = SharedSqliteDatabase.in(dir);
        DataOperator<PlayerTradeSettings> table = database.openAs(PlayerTradeSettings.class);
        Player sender = UltiTradeTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        Player target = UltiTradeTestHelper.createMockPlayer("Target", UUID.randomUUID());
        PlayerTradeSettings row = new PlayerTradeSettings(sender.getUniqueId(), sender.getName());
        if ("unblock".equals(operation)) {
            row.blockPlayer(target.getUniqueId().toString());
        }
        table.insert(row);
        table.insert(new PlayerTradeSettings(target.getUniqueId(), target.getName()));
        doReturn(target).when(Bukkit.getServer()).getPlayerExact("Target");
        World world = mock(World.class);
        when(sender.getWorld()).thenReturn(world);
        when(target.getWorld()).thenReturn(world);
        when(sender.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(target.getLocation()).thenReturn(new Location(world, 1, 64, 0));

        TradeConfig config = UltiTradeTestHelper.createDefaultConfig();
        when(config.isEnableSounds()).thenReturn(false);
        when(config.isEnableBossbar()).thenReturn(false);
        when(config.isEnableClickableButtons()).thenReturn(false);
        TradeLogService logs = new TradeLogService();
        UltiTradeTestHelper.setField(logs, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(logs, "settingsOperator", table);
        List<Long> allowances = new ArrayList<>();
        installContendedLock(logs, sender.getUniqueId(), allowances);
        installContendedLock(logs, target.getUniqueId(), allowances);

        TradeService trades = new TradeService();
        UltiTradeTestHelper.setField(trades, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(trades, "config", config);
        UltiTradeTestHelper.setField(trades, "logService", logs);
        TradeCommand command = new TradeCommand(UltiTradeTestHelper.getMockPlugin(), trades, logs);
        if ("block".equals(operation)) {
            command.blockPlayer(sender, "Target");
        } else if ("unblock".equals(operation)) {
            command.unblockPlayer(sender, "Target");
        } else {
            assertThat(trades.sendRequest(sender, target)).isTrue();
        }

        assertThat(allowances).hasSize("request".equals(operation) ? 4 : 2);
        assertThat(allowances.stream().mapToLong(Long::longValue).sum())
                .as("one operation allocates at most 50 ms across every settings lock")
                .isLessThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(TradeLogService.MAIN_THREAD_WAIT_MILLIS));
        assertThat(allowances.subList(1, allowances.size())).containsOnly(0L);
        assertThat(table.getById(row.getId()).getBlockedPlayersJson()).isEqualTo(row.getBlockedPlayersJson());

        // The operation must not leave an expired deadline attached to this reusable server thread.
        allowances.clear();
        logs.getSettings(sender.getUniqueId());
        assertThat(allowances).hasSize(1);
        assertThat(allowances.get(0)).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"block", "unblock", "request"})
    void exceptionAtEachOperationBoundaryClearsTheDeadline(String operation) throws Exception {
        TradeLogService logs = mock(TradeLogService.class);
        Player sender = UltiTradeTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        Player target = UltiTradeTestHelper.createMockPlayer("Target", UUID.randomUUID());
        doReturn(target).when(Bukkit.getServer()).getPlayerExact("Target");
        when(logs.isBlocked(any(), any())).thenThrow(new IllegalStateException("read failed"));
        when(logs.isTradeEnabled(any())).thenThrow(new IllegalStateException("read failed"));
        TradeService trades = new TradeService();
        UltiTradeTestHelper.setField(trades, "logService", logs);
        TradeCommand command = new TradeCommand(UltiTradeTestHelper.getMockPlugin(), trades, logs);
        assertThatThrownBy(() -> {
            if ("block".equals(operation)) {
                command.blockPlayer(sender, "Target");
            } else if ("unblock".equals(operation)) {
                command.unblockPlayer(sender, "Target");
            } else {
                trades.sendRequest(sender, target);
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(SettingsWaitBudget.remainingNanos())
                .isEqualTo(TimeUnit.MILLISECONDS.toNanos(TradeLogService.MAIN_THREAD_WAIT_MILLIS));
    }

    @Test
    void expiredBudgetStillAllowsAnUncontendedWrite() throws Exception {
        TradeLogService logs = new TradeLogService();
        SharedSqliteDatabase database = SharedSqliteDatabase.in(dir);
        UltiTradeTestHelper.setField(logs, "plugin", UltiTradeTestHelper.getMockPlugin());
        UltiTradeTestHelper.setField(logs, "settingsOperator", database.openAs(PlayerTradeSettings.class));
        Player player = UltiTradeTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        try (SettingsWaitBudget budget = SettingsWaitBudget.open()) {
            TimeUnit.MILLISECONDS.sleep(TradeLogService.MAIN_THREAD_WAIT_MILLIS + 10);
            assertThat(logs.toggle(player).getWrite()).isEqualTo(TradeLogService.SettingsWrite.WRITTEN);
        }
    }

    /** A counted holder keeps the entry present while each attempted acquisition times out. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void installContendedLock(TradeLogService logs, UUID player, List<Long> allowances) throws Exception {
        Class<?> type = Class.forName(TradeLogService.class.getName() + "$PlayerLock");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object entry = constructor.newInstance();
        ReentrantLock lock = mock(ReentrantLock.class);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            long nanos = ((TimeUnit) invocation.getArgument(1)).toNanos(invocation.getArgument(0));
            boolean first = allowances.isEmpty();
            allowances.add(nanos);
            if (first) {
                // Deliberately exhaust the first allowance; later attempts must be immediate, even for another UUID.
                TimeUnit.MILLISECONDS.sleep(TradeLogService.MAIN_THREAD_WAIT_MILLIS + 10);
            }
            return false;
        });
        UltiTradeTestHelper.setField(entry, "lock", lock);
        UltiTradeTestHelper.setField(entry, "users", 1);
        Map<UUID, Object> locks = UltiTradeTestHelper.getField(logs, "writeLocks");
        locks.put(player, entry);
    }
}
