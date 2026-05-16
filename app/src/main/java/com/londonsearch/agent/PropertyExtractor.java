package com.londonsearch.agent;

import java.util.List;

public interface PropertyExtractor {
    List<ExtractedProperty> extract(String html, String siteName);
}
