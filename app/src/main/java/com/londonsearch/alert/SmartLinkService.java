package com.londonsearch.alert;

import com.londonsearch.model.AlertRecord;
import com.londonsearch.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SmartLinkService {

    private static final Logger log = LoggerFactory.getLogger(SmartLinkService.class);

    private final AlertRepository alertRepository;
    private final String portalBaseUrl;
    private final String emailTo;

    public SmartLinkService(
            AlertRepository alertRepository,
            @Value("${app.alert.portal-base-url}") String portalBaseUrl,
            @Value("${app.alert.email-to}") String emailTo) {
        this.alertRepository = alertRepository;
        this.portalBaseUrl = portalBaseUrl;
        this.emailTo = emailTo;
    }

    public String generateSmartLink(List<String> propertyIds, String emailTo, int newCount) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AlertRecord record = new AlertRecord();
        record.setId(UUID.randomUUID().toString());
        record.setPropertyIds(propertyIds);
        record.setEmailSentTo(emailTo);
        record.setSentAt(now);
        record.setSmartLinkToken(token);
        record.setTokenExpiresAt(now.plus(24, ChronoUnit.HOURS));
        record.setNewPropertyCount(newCount);

        alertRepository.save(record);
        log.info("Generated smart link token {} for {} properties (expires in 24h)", token, newCount);

        return portalBaseUrl + "/alert/" + token;
    }

    public Optional<AlertRecord> validateToken(String token) {
        Optional<AlertRecord> record = alertRepository.findByToken(token);

        if (record.isEmpty()) {
            log.warn("Smart link token not found: {}", token);
            return Optional.empty();
        }

        AlertRecord alert = record.get();
        if (alert.getTokenExpiresAt() != null && Instant.now().isAfter(alert.getTokenExpiresAt())) {
            log.warn("Smart link token expired: {}", token);
            return Optional.empty();
        }

        return record;
    }
}
