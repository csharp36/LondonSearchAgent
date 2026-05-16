package com.londonsearch.agent;

import com.londonsearch.model.Property;
import com.londonsearch.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AgentPipelineServiceTest {

    @Autowired
    private AgentPipelineService pipelineService;

    @Autowired
    private PropertyRepository propertyRepository;

    @BeforeEach
    void cleanupTestAddresses() {
        // Remove any previously saved test fixtures so tests are idempotent across runs
        propertyRepository.findAll().stream()
                .filter(p -> {
                    String addr = p.getNormalizedAddress();
                    return addr != null && (
                            addr.equals("99 grosvenor square, london w1k 1aa") ||
                            addr.startsWith("flat 1, welbeck way")
                    );
                })
                .forEach(p -> propertyRepository.delete(p.getId()));
    }

    @Test
    void pipelineServiceBeanExists() {
        assertThat(pipelineService).isNotNull();
    }

    @Test
    void processExtractedProperties_savesNewProperties() {
        List<ExtractedProperty> extracted = List.of(
                new ExtractedProperty(
                        "99 Grosvenor Square, London W1K 1AA",
                        "£7,000 pcm",
                        "2", "1", "900", "Flat", "Furnished",
                        "A stunning property in Mayfair",
                        "https://example.com/property/test-001",
                        List.of(), null, "Knight Frank", null, null
                )
        );

        AgentPipelineService.PipelineResult result =
                pipelineService.processExtractedProperties(extracted, "TestSite", "https://example.com");

        assertThat(result.newProperties()).isEqualTo(1);
        assertThat(result.updatedProperties()).isEqualTo(0);

        List<Property> mayfairProps = propertyRepository.findByArea("Mayfair");
        boolean found = mayfairProps.stream()
                .anyMatch(p -> p.getAddress().equals("99 Grosvenor Square, London W1K 1AA"));
        assertThat(found).isTrue();
    }

    @Test
    void processExtractedProperties_skipsExistingByAddress() {
        String uniqueAddr = "Flat 1, Welbeck Way, London W1W " + java.util.UUID.randomUUID().toString().substring(0, 4);
        List<ExtractedProperty> extracted = List.of(
                new ExtractedProperty(
                        uniqueAddr,
                        "£5,000 pcm",
                        "1", "1", "500", "Flat", "Unfurnished",
                        "A spacious flat near Marylebone",
                        "https://example.com/property/dedup-001",
                        List.of(), null, null, null, null
                )
        );

        AgentPipelineService.PipelineResult result1 =
                pipelineService.processExtractedProperties(extracted, "SiteA", "https://sitea.com");
        assertThat(result1.newProperties()).isEqualTo(1);

        AgentPipelineService.PipelineResult result2 =
                pipelineService.processExtractedProperties(extracted, "SiteB", "https://siteb.com");
        assertThat(result2.newProperties()).isEqualTo(0);
        assertThat(result2.updatedProperties()).isEqualTo(1);
    }
}
