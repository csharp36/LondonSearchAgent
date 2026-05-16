package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.model.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "mock", matchIfMissing = true)
public class MockIntelligence implements PropertyIntelligence {

    private static final Logger log = LoggerFactory.getLogger(MockIntelligence.class);

    @Override
    public Assessment assess(Property property, SearchConfig config) {
        String area = property.getArea() != null ? property.getArea() : "";
        int bedrooms = property.getBedrooms() != null ? property.getBedrooms() : 0;
        int price = property.getPricePerMonth() != null ? property.getPricePerMonth()
                : (property.getPrice() != null ? property.getPrice() : 0);
        String type = property.getPropertyType() != null ? property.getPropertyType() : "property";
        String furnishing = property.getFurnishing() != null ? property.getFurnishing() : "unfurnished";

        String locationInsight;
        int aiScore;

        if (area.equalsIgnoreCase("Mayfair")) {
            locationInsight = "Strong restaurant access — one of London's premier dining destinations.";
            aiScore = 80;
        } else if (area.equalsIgnoreCase("Marylebone")) {
            locationInsight = "Excellent village feel with independent shops along Marylebone High Street.";
            aiScore = 80;
        } else if (area.equalsIgnoreCase("South Kensington")) {
            locationInsight = "Museum quarter with quiet residential streets. Good tube access.";
            aiScore = 80;
        } else {
            locationInsight = "Location outside primary target areas.";
            aiScore = 60;
        }

        String summary = String.format(
                "This %s-bedroom %s (%s) in %s is priced at £%,d pcm. %s",
                bedrooms, type.toLowerCase(), furnishing.toLowerCase(), area, price, locationInsight
        );

        log.info("MockIntelligence: assessed property in {} — score {}", area, aiScore);
        return new Assessment(summary, aiScore);
    }
}
