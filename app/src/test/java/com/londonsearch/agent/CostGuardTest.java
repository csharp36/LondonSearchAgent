package com.londonsearch.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CostGuardTest {

    @Test
    void disabledAllowsProceed() {
        CostGuard guard = new CostGuard(false, 50);

        assertThat(guard.canProceed()).isTrue();
    }

    @Test
    void enabledBlocksProceed() {
        CostGuard guard = new CostGuard(true, 50);

        assertThat(guard.canProceed()).isFalse();
    }

    @Test
    void reportsEnabledState() {
        assertThat(new CostGuard(true, 100).isEnabled()).isTrue();
        assertThat(new CostGuard(false, 100).isEnabled()).isFalse();
    }

    @Test
    void reportsMonthlyBudget() {
        assertThat(new CostGuard(false, 75).getMonthlyBudget()).isEqualTo(75);
    }
}
