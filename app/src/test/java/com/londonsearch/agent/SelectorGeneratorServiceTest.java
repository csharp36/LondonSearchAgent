package com.londonsearch.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectorGeneratorServiceTest {

    @Mock(answer = RETURNS_DEEP_STUBS)
    private AnthropicClient anthropicClient;

    private SelectorGeneratorService service;

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
        service = new SelectorGeneratorService(anthropicClient, new CssSelectorExtractor(), "claude-sonnet-4-20250514");
    }

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

    @Test
    void invalidSelectorsReturnEmpty() {
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

    @Test
    void malformedJsonResponseReturnEmpty() {
        stubResponse("I'm sorry, I could not find any CSS selectors for this page.");

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

    @Test
    void apiExceptionReturnEmpty() {
        when(anthropicClient.messages().create(any(MessageCreateParams.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        Optional<SelectorGeneratorService.GenerationResult> result =
                service.generateAndValidate(SAMPLE_HTML, "TestSite");

        assertThat(result).isEmpty();
    }

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

    @SuppressWarnings("unchecked")
    private void stubResponse(String responseText) {
        ContentBlock block = mock(ContentBlock.class);
        TextBlock textBlock = mock(TextBlock.class);
        when(textBlock.text()).thenReturn(responseText);
        when(block.text()).thenReturn(Optional.of(textBlock));

        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of(block));
        when(anthropicClient.messages().create(any(MessageCreateParams.class))).thenReturn(message);
    }
}
