package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;

public interface PropertyIntelligence {
    Assessment assess(Property property, SearchConfig config);
    record Assessment(String aiSummary, int aiScore) {}
}
