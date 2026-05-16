package com.londonsearch.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CostGuard {

    private static final Logger log = LoggerFactory.getLogger(CostGuard.class);

    private final boolean enabled;
    private final int monthlyBudget;

    public CostGuard(
            @Value("${app.agent.cost-guard.enabled:false}") boolean enabled,
            @Value("${app.agent.cost-guard.monthly-budget:50}") int monthlyBudget) {
        this.enabled = enabled;
        this.monthlyBudget = monthlyBudget;
    }

    public boolean canProceed() {
        if (!enabled) return true;
        log.warn("Cost guard is enabled — pipeline blocked. Set COST_GUARD_ENABLED=false to resume.");
        return false;
    }

    public boolean isEnabled() { return enabled; }
    public int getMonthlyBudget() { return monthlyBudget; }
}
