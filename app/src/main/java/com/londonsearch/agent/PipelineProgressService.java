package com.londonsearch.agent;

import com.londonsearch.model.MonitoredSite;
import com.londonsearch.repository.MonitoredSiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PipelineProgressService {

    private static final Logger log = LoggerFactory.getLogger(PipelineProgressService.class);

    private final AgentPipelineService pipelineService;
    private final MonitoredSiteRepository siteRepo;
    private final AtomicReference<PipelineStatus> currentStatus = new AtomicReference<>(idle());

    public PipelineProgressService(AgentPipelineService pipelineService,
                                    MonitoredSiteRepository siteRepo) {
        this.pipelineService = pipelineService;
        this.siteRepo = siteRepo;
    }

    public boolean isRunning() {
        return "running".equals(currentStatus.get().phase());
    }

    public PipelineStatus getStatus() {
        return currentStatus.get();
    }

    /** Starts pipeline in a virtual thread. Returns false if already running. */
    public boolean startAsync() {
        if (isRunning()) return false;

        List<MonitoredSite> sites = siteRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()))
                .toList();

        int total = sites.size();
        currentStatus.set(new PipelineStatus("running", 0, total, "",
                new ArrayList<>(), 0, 0, null, Instant.now()));

        Thread.startVirtualThread(() -> runWithProgress(sites));
        return true;
    }

    private void runWithProgress(List<MonitoredSite> sites) {
        int processed = 0;
        int totalNew = 0, totalUpdated = 0;
        List<String> siteResults = Collections.synchronizedList(new ArrayList<>());

        try {
            for (MonitoredSite site : sites) {
                String siteName = site.getName();
                currentStatus.set(new PipelineStatus("running", processed, sites.size(),
                        siteName, new ArrayList<>(siteResults), totalNew, totalUpdated, null, currentStatus.get().startedAt()));

                try {
                    AgentPipelineService.SiteResult result = pipelineService.processSite(site);
                    if (result.skipped()) {
                        siteResults.add(siteName + ": skipped");
                    } else {
                        int n = result.pipelineResult().newProperties();
                        int u = result.pipelineResult().updatedProperties();
                        totalNew += n;
                        totalUpdated += u;
                        siteResults.add(siteName + ": " + n + " new, " + u + " updated");
                    }
                } catch (Exception e) {
                    log.error("Error processing site {}: {}", siteName, e.getMessage());
                    siteResults.add(siteName + ": error — " + e.getMessage());
                }
                processed++;
            }

            currentStatus.set(new PipelineStatus("complete", processed, sites.size(),
                    "", new ArrayList<>(siteResults), totalNew, totalUpdated, null, currentStatus.get().startedAt()));
        } catch (Exception e) {
            log.error("Pipeline failed: {}", e.getMessage());
            currentStatus.set(new PipelineStatus("error", processed, sites.size(),
                    "", new ArrayList<>(siteResults), totalNew, totalUpdated, e.getMessage(), currentStatus.get().startedAt()));
        }
    }

    private static PipelineStatus idle() {
        return new PipelineStatus("idle", 0, 0, "", List.of(), 0, 0, null, null);
    }

    public record PipelineStatus(
            String phase,
            int sitesProcessed,
            int sitesTotal,
            String currentSite,
            List<String> siteResults,
            int newProperties,
            int updatedProperties,
            String error,
            Instant startedAt
    ) {}
}
