package com.londonsearch.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedrockExtractorTest {

    @Mock
    private BedrockRuntimeClient bedrockClient;

    private BedrockExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new BedrockExtractor(bedrockClient, "amazon.nova-micro-v1:0");
    }

    @Test
    void cleanJsonArrayResponse() {
        stubResponse("""
                [{"address":"42 Baker Street, London W1U 3BW","price":"£7,500 pcm","bedrooms":"3","bathrooms":"2","sqft":null,"propertyType":"Flat","furnishing":"Furnished","description":"Beautiful flat","listingUrl":"https://example.com/42","imageUrls":["https://example.com/img.jpg"],"floorPlanUrl":null,"availableFrom":"2026-07-01","agentName":"Foxtons","agentPhone":null,"agentEmail":null}]
                """);

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).address()).isEqualTo("42 Baker Street, London W1U 3BW");
        assertThat(results.get(0).price()).isEqualTo("£7,500 pcm");
        assertThat(results.get(0).bedrooms()).isEqualTo("3");
        assertThat(results.get(0).imageUrls()).containsExactly("https://example.com/img.jpg");
    }

    @Test
    void stripsJsonCodeFence() {
        stubResponse("""
                ```json
                [{"address":"15 Mount Street, Mayfair W1K 2RN","price":"£5,000 pcm","bedrooms":"2","bathrooms":"1","sqft":null,"propertyType":"Flat","furnishing":null,"description":null,"listingUrl":null,"imageUrls":null,"floorPlanUrl":null,"availableFrom":null,"agentName":null,"agentPhone":null,"agentEmail":null}]
                ```
                """);

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).address()).isEqualTo("15 Mount Street, Mayfair W1K 2RN");
    }

    @Test
    void stripsPlainCodeFence() {
        stubResponse("""
                ```
                [{"address":"8 Onslow Gardens, SW7 3AQ","price":"£3,000 pcm","bedrooms":"1","bathrooms":"1","sqft":null,"propertyType":"Flat","furnishing":null,"description":null,"listingUrl":null,"imageUrls":null,"floorPlanUrl":null,"availableFrom":null,"agentName":null,"agentPhone":null,"agentEmail":null}]
                ```
                """);

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).address()).isEqualTo("8 Onslow Gardens, SW7 3AQ");
    }

    @Test
    void extractsJsonArrayFromSurroundingText() {
        stubResponse("""
                Here are the listings I found:
                [{"address":"10 Wimpole Street, W1G 8GQ","price":"£4,200 pcm","bedrooms":"2","bathrooms":"1","sqft":null,"propertyType":"Flat","furnishing":null,"description":null,"listingUrl":null,"imageUrls":null,"floorPlanUrl":null,"availableFrom":null,"agentName":null,"agentPhone":null,"agentEmail":null}]
                I found 1 listing in total.
                """);

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).address()).isEqualTo("10 Wimpole Street, W1G 8GQ");
    }

    @Test
    void emptyArrayResponse() {
        stubResponse("[]");

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).isEmpty();
    }

    @Test
    void multipleProperties() {
        stubResponse("""
                [
                  {"address":"Flat 1, 22 Welbeck Way","price":"£6,000 pcm","bedrooms":"2","bathrooms":"1","sqft":null,"propertyType":"Flat","furnishing":null,"description":null,"listingUrl":null,"imageUrls":null,"floorPlanUrl":null,"availableFrom":null,"agentName":null,"agentPhone":null,"agentEmail":null},
                  {"address":"Flat 2, 22 Welbeck Way","price":"£7,000 pcm","bedrooms":"3","bathrooms":"2","sqft":null,"propertyType":"Flat","furnishing":null,"description":null,"listingUrl":null,"imageUrls":null,"floorPlanUrl":null,"availableFrom":null,"agentName":null,"agentPhone":null,"agentEmail":null}
                ]
                """);

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).address()).isEqualTo("Flat 1, 22 Welbeck Way");
        assertThat(results.get(1).address()).isEqualTo("Flat 2, 22 Welbeck Way");
    }

    @Test
    void unknownFieldsAreIgnored() {
        stubResponse("""
                [{"address":"42 Baker Street","price":"£5,000 pcm","bedrooms":"2","bathrooms":"1","sqft":null,"propertyType":"Flat","furnishing":null,"description":null,"listingUrl":null,"imageUrls":null,"floorPlanUrl":null,"availableFrom":null,"agentName":null,"agentPhone":null,"agentEmail":null,"unknownField":"value"}]
                """);

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).hasSize(1);
    }

    @Test
    void malformedJsonReturnsEmptyList() {
        stubResponse("this is not json at all");

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).isEmpty();
    }

    @Test
    void bedrockExceptionReturnsEmptyList() {
        when(bedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(new RuntimeException("Bedrock service unavailable"));

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).isEmpty();
    }

    @Test
    void nullFieldsDeserializeGracefully() {
        stubResponse("""
                [{"address":"42 Baker Street","price":null,"bedrooms":null,"bathrooms":null,"sqft":null,"propertyType":null,"furnishing":null,"description":null,"listingUrl":null,"imageUrls":null,"floorPlanUrl":null,"availableFrom":null,"agentName":null,"agentPhone":null,"agentEmail":null}]
                """);

        List<ExtractedProperty> results = extractor.extract("<html>test</html>", "TestSite");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).address()).isEqualTo("42 Baker Street");
        assertThat(results.get(0).price()).isNull();
        assertThat(results.get(0).imageUrls()).isNull();
    }

    private void stubResponse(String responseText) {
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText(responseText))
                                .build())
                        .build())
                .build();
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(response);
    }
}
