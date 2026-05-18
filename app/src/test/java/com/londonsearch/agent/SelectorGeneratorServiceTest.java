package com.londonsearch.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectorGeneratorServiceTest {

    @Mock
    private BedrockRuntimeClient bedrockClient;

    private SelectorGeneratorService service;

    /**
     * Sample HTML with 3 repeating .property-card elements (needed for card detection).
     */
    private static final String SAMPLE_HTML = """
            <html><body>
              <div class="results">
                <div class="property-card">
                  <span class="address">15 Mount Street, Mayfair, London W1K 2RN</span>
                  <span class="price">£7,500 pcm</span>
                  <span class="beds">3</span>
                  <a class="link" href="/listings/mount-street">View listing</a>
                  <img class="photo" src="https://example.com/photo1.jpg" />
                </div>
                <div class="property-card">
                  <span class="address">42 Baker Street, London W1U 3BW</span>
                  <span class="price">£5,000 pcm</span>
                  <span class="beds">2</span>
                  <a class="link" href="/listings/baker-street">View listing</a>
                  <img class="photo" src="https://example.com/photo2.jpg" />
                </div>
                <div class="property-card">
                  <span class="address">8 Onslow Gardens, London SW7 3AQ</span>
                  <span class="price">£6,200 pcm</span>
                  <span class="beds">2</span>
                  <a class="link" href="/listings/onslow">View listing</a>
                  <img class="photo" src="https://example.com/photo3.jpg" />
                </div>
              </div>
            </body></html>
            """;

    @BeforeEach
    void setUp() {
        service = new SelectorGeneratorService(bedrockClient, new CssSelectorExtractor(), "us.anthropic.claude-sonnet-4-6");
    }

    // ── Test 1: validSelectorsReturnResults ──────────────────────────────────

    @Test
    void validSelectorsReturnResults() {
        stubResponse("""
                {
                  "listingContainer": ".property-card",
                  "address": ".address",
                  "price": ".price",
                  "bedrooms": ".beds",
                  "listingUrl": ".link @href",
                  "imageUrl": ".photo @src"
                }
                """);

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isPresent();
        assertThat(result.get().selectors()).containsKey("listingContainer");
        assertThat(result.get().selectors().get("listingContainer")).isEqualTo(".property-card");
        assertThat(result.get().results()).hasSize(3);
        assertThat(result.get().results().get(0).address()).isEqualTo("15 Mount Street, Mayfair, London W1K 2RN");
        assertThat(result.get().results().get(0).price()).isEqualTo("£7,500 pcm");
    }

    // ── Test 2: invalidSelectorsReturnEmpty ──────────────────────────────────

    @Test
    void invalidSelectorsReturnEmpty() {
        // Selectors that won't match anything in the sample HTML
        stubResponse("""
                {
                  "listingContainer": ".no-such-container",
                  "address": ".no-such-address",
                  "price": ".no-such-price"
                }
                """);

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

    // ── Test 3: malformedJsonResponseReturnEmpty ─────────────────────────────

    @Test
    void malformedJsonResponseReturnEmpty() {
        stubResponse("I'm sorry, I could not find any CSS selectors for this page.");

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

    // ── Test 4: bedrockExceptionReturnEmpty ──────────────────────────────────

    @Test
    void bedrockExceptionReturnEmpty() {
        when(bedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(new RuntimeException("Bedrock service unavailable"));

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

    // ── Test 5: jsonWrappedInCodeFencesIsParsed ──────────────────────────────

    @Test
    void jsonWrappedInCodeFencesIsParsed() {
        stubResponse("""
                ```json
                {
                  "listingContainer": ".property-card",
                  "address": ".address",
                  "price": ".price"
                }
                ```
                """);

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isPresent();
        assertThat(result.get().selectors()).containsKey("listingContainer");
        assertThat(result.get().results()).hasSize(3);
        assertThat(result.get().results().get(0).address()).isEqualTo("15 Mount Street, Mayfair, London W1K 2RN");
        assertThat(result.get().results().get(0).price()).isEqualTo("£7,500 pcm");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

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
