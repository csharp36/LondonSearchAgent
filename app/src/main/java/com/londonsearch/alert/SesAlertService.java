package com.londonsearch.alert;

import com.londonsearch.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.agent.extractor", havingValue = "bedrock")
public class SesAlertService implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(SesAlertService.class);

    private final String emailTo;
    private final String emailFrom;
    private final SesClient sesClient;

    public SesAlertService(
            @Value("${app.alert.email-to}") String emailTo,
            @Value("${app.alert.email-from:noreply@londonsearchagent.com}") String emailFrom,
            @Value("${app.aws.region}") String region) {
        this.emailTo = emailTo;
        this.emailFrom = emailFrom;
        this.sesClient = SesClient.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public void sendNewPropertiesAlert(List<Property> newProperties, String smartLinkUrl) {
        if (newProperties == null || newProperties.isEmpty()) {
            log.info("No new properties to alert on, skipping SES email.");
            return;
        }

        try {
            String subject = String.format("LondonSearchAgent: %d new propert%s",
                    newProperties.size(),
                    newProperties.size() == 1 ? "y" : "ies");

            String htmlBody = buildHtmlBody(newProperties, smartLinkUrl);

            SendEmailRequest request = SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(emailTo).build())
                    .source(emailFrom)
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
            log.info("SES alert email sent to {} for {} new properties.", emailTo, newProperties.size());
        } catch (Exception e) {
            log.error("Failed to send SES alert email to {}: {}", emailTo, e.getMessage(), e);
        }
    }

    private String buildHtmlBody(List<Property> properties, String smartLinkUrl) {
        StringBuilder cards = new StringBuilder();
        for (Property p : properties) {
            Integer displayPrice = p.getPricePerMonth() != null ? p.getPricePerMonth() : p.getPrice();
            String priceStr = displayPrice != null ? String.format("£%,d pcm", displayPrice) : "Price on request";
            String bedsStr = p.getBedrooms() != null ? p.getBedrooms() + " bed" : "N/A";
            String areaStr = p.getArea() != null ? p.getArea() : "";
            String addressStr = p.getAddress() != null ? p.getAddress() : "Address not available";
            String scoreStr = p.getMatchScore() != null ? p.getMatchScore() + "%" : "—";

            String summarySnippet = "";
            if (p.getAiSummary() != null && !p.getAiSummary().isBlank()) {
                String raw = p.getAiSummary();
                summarySnippet = raw.length() > 180 ? raw.substring(0, 177) + "..." : raw;
            }

            cards.append(String.format("""
                    <div style="background:#1e293b;border-radius:10px;padding:20px;margin-bottom:16px;border:1px solid #334155;">
                      <div style="display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:8px;">
                        <div>
                          <div style="font-size:16px;font-weight:600;color:#f1f5f9;">%s</div>
                          <div style="font-size:13px;color:#94a3b8;margin-top:2px;">%s</div>
                        </div>
                        <div style="text-align:right;">
                          <div style="font-size:15px;font-weight:700;color:#38bdf8;">%s</div>
                          <div style="font-size:12px;color:#64748b;margin-top:2px;">%s</div>
                        </div>
                      </div>
                      <div style="display:flex;gap:16px;margin-bottom:%s;">
                        <span style="background:#0f172a;color:#94a3b8;padding:3px 10px;border-radius:20px;font-size:12px;">%s</span>
                        <span style="background:#1e3a5f;color:#38bdf8;padding:3px 10px;border-radius:20px;font-size:12px;">Match: %s</span>
                      </div>
                      %s
                    </div>
                    """,
                    escapeHtml(addressStr),
                    escapeHtml(areaStr),
                    priceStr,
                    bedsStr,
                    summarySnippet.isEmpty() ? "0" : "8px",
                    escapeHtml(bedsStr),
                    escapeHtml(scoreStr),
                    summarySnippet.isEmpty() ? "" : String.format(
                            "<div style=\"font-size:13px;color:#94a3b8;font-style:italic;\">%s</div>",
                            escapeHtml(summarySnippet))
            ));
        }

        String viewButtonHtml = smartLinkUrl != null && !smartLinkUrl.isBlank()
                ? String.format("""
                <div style="text-align:center;margin-top:28px;">
                  <a href="%s" style="background:#2563eb;color:#ffffff;text-decoration:none;padding:14px 36px;border-radius:8px;font-size:15px;font-weight:600;display:inline-block;">View in Portal</a>
                </div>
                """, escapeHtml(smartLinkUrl))
                : "";

        return String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>LondonSearchAgent Alert</title>
                </head>
                <body style="margin:0;padding:0;background:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                  <div style="max-width:640px;margin:0 auto;padding:32px 16px;">
                    <div style="text-align:center;margin-bottom:28px;">
                      <h1 style="color:#38bdf8;font-size:24px;margin:0 0 6px;">LondonSearch<span style="color:#f1f5f9;">Agent</span></h1>
                      <p style="color:#94a3b8;font-size:15px;margin:0;">%d new propert%s found matching your criteria</p>
                    </div>
                    %s
                    %s
                    <div style="text-align:center;margin-top:32px;color:#475569;font-size:12px;">
                      You're receiving this because you set up a property alert. &copy; LondonSearchAgent
                    </div>
                  </div>
                </body>
                </html>
                """,
                properties.size(),
                properties.size() == 1 ? "y" : "ies",
                cards,
                viewButtonHtml
        );
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
