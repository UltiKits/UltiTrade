package com.ultikits.plugins.trade.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsWaitBudgetTest {
    private static final long FULL = TimeUnit.MILLISECONDS.toNanos(SettingsWaitBudget.WAIT_MILLIS);

    @Test
    void standaloneAcquisitionsKeepTheOriginalAllowance() {
        assertThat(SettingsWaitBudget.remainingNanos()).isEqualTo(FULL);
        assertThat(SettingsWaitBudget.remainingNanos()).isEqualTo(FULL);
    }

    @Test
    void nestedScopesNeitherResetNorRemoveTheOuterDeadline() throws Exception {
        try (SettingsWaitBudget outer = SettingsWaitBudget.open()) {
            TimeUnit.MILLISECONDS.sleep(SettingsWaitBudget.WAIT_MILLIS + 10);
            try (SettingsWaitBudget nested = SettingsWaitBudget.open()) {
                assertThat(SettingsWaitBudget.remainingNanos()).isZero();
            }
            assertThat(SettingsWaitBudget.remainingNanos()).isZero();
        }
        assertThat(SettingsWaitBudget.remainingNanos()).isEqualTo(FULL);
    }

    @Test
    void exceptionRemovesTheDeadline() {
        assertThatThrownBy(() -> {
            try (SettingsWaitBudget budget = SettingsWaitBudget.open()) {
                throw new IllegalStateException("operation failed");
            }
        }).isInstanceOf(IllegalStateException.class);
        assertThat(SettingsWaitBudget.remainingNanos()).isEqualTo(FULL);
    }

    @Test
    void childThreadDoesNotInheritTheExpiredDeadline() throws Exception {
        AtomicLong childAllowance = new AtomicLong(-1);
        try (SettingsWaitBudget budget = SettingsWaitBudget.open()) {
            TimeUnit.MILLISECONDS.sleep(SettingsWaitBudget.WAIT_MILLIS + 10);
            Thread child = new Thread(() -> childAllowance.set(SettingsWaitBudget.remainingNanos()));
            child.start();
            child.join();
            assertThat(SettingsWaitBudget.remainingNanos()).isZero();
            assertThat(childAllowance.get()).isEqualTo(FULL);
        }
        assertThat(SettingsWaitBudget.remainingNanos()).isEqualTo(FULL);
    }
}
