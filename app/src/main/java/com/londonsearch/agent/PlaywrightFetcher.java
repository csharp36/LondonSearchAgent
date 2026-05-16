package com.londonsearch.agent;

import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.Optional;

/**
 * Fetches pages using a headless Chromium browser via Playwright.
 * Used for js-rendered sites where Jsoup can't execute JavaScript.
 */
@Service
public class PlaywrightFetcher {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightFetcher.class);

    private Playwright playwright;
    private Browser browser;

    private synchronized Browser getBrowser() {
        if (browser == null || !browser.isConnected()) {
            if (playwright == null) {
                playwright = Playwright.create();
            }
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true));
            log.info("PlaywrightFetcher: launched headless Chromium");
        }
        return browser;
    }

    public Optional<SiteFetcher.FetchResult> fetch(String url) {
        try {
            Browser b = getBrowser();
            BrowserContext context = b.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"));

            Page page = context.newPage();
            page.setDefaultTimeout(30000);

            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));

            // Wait a moment for any late-loading content
            page.waitForTimeout(2000);

            String html = page.content();
            String hash = SiteFetcher.computeHash(html);

            page.close();
            context.close();

            log.info("PlaywrightFetcher: fetched {} ({} chars)", url, html.length());
            return Optional.of(new SiteFetcher.FetchResult(html, hash, url));
        } catch (Exception e) {
            log.error("PlaywrightFetcher: failed to fetch {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
    }
}
