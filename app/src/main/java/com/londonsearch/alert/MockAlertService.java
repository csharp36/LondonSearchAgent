package com.londonsearch.alert;

import com.londonsearch.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "mock", matchIfMissing = true)
public class MockAlertService implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(MockAlertService.class);

    @Value("${app.alert.email-to}")
    private String emailTo;

    @Override
    public void sendNewPropertiesAlert(List<Property> newProperties, String smartLinkUrl) {
        log.info("=== MOCK ALERT EMAIL ===");
        log.info("To: {}", emailTo);
        log.info("Subject: {} new propert{} found in London Search", newProperties.size(),
                newProperties.size() == 1 ? "y" : "ies");
        log.info("--- Property Summaries ---");
        for (Property p : newProperties) {
            log.info("  [{}] {} | {} bed | £{} pcm | Score: {}",
                    p.getArea(),
                    p.getAddress(),
                    p.getBedrooms(),
                    p.getPricePerMonth() != null ? p.getPricePerMonth() : p.getPrice(),
                    p.getMatchScore());
        }
        log.info("Smart Link URL: {}", smartLinkUrl);
        log.info("=== END MOCK ALERT EMAIL ===");
    }
}
