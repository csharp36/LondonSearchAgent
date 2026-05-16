package com.londonsearch.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "mock", matchIfMissing = true)
public class MockExtractor implements PropertyExtractor {

    private static final Logger log = LoggerFactory.getLogger(MockExtractor.class);

    @Override
    public List<ExtractedProperty> extract(String html, String siteName) {
        log.info("MockExtractor: simulating extraction from {} ({} chars of HTML)", siteName, html.length());

        return List.of(
                new ExtractedProperty(
                        "Flat 4, 18 Weymouth Street, London W1W 5BU",
                        "£6,500 pcm",
                        "2", "2", "850", "Flat", "Furnished",
                        "A stylish two bedroom apartment in this popular Marylebone street, moments from Regent's Park.",
                        "https://www." + siteName.toLowerCase().replace(" ", "") + ".co.uk/property/mock-001",
                        List.of(), null, "Available now",
                        siteName + " Lettings", "020 7946 0001", null
                ),
                new ExtractedProperty(
                        "23 Curzon Street, Mayfair, London W1J 7TN",
                        "£8,750 pcm",
                        "3", "2", "1,100", "Flat", "Furnished",
                        "An exceptional three bedroom apartment in the heart of Mayfair with views over the rooftops.",
                        "https://www." + siteName.toLowerCase().replace(" ", "") + ".co.uk/property/mock-002",
                        List.of(), null, "01/07/2026",
                        siteName + " Lettings", "020 7946 0002", null
                )
        );
    }
}
