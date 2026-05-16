package com.londonsearch.agent;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentPipelineService pipelineService;
    private final PipelineProgressService progressService;

    public AgentController(AgentPipelineService pipelineService,
                           PipelineProgressService progressService) {
        this.pipelineService = pipelineService;
        this.progressService = progressService;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runPipeline() {
        AgentPipelineService.RunResult result = pipelineService.runFullPipeline();
        return ResponseEntity.ok(Map.of(
                "status", result.errors().isEmpty() ? "success" : "completed_with_errors",
                "sitesProcessed", result.sitesProcessed(),
                "sitesSkipped", result.sitesSkipped(),
                "newProperties", result.newProperties(),
                "updatedProperties", result.updatedProperties(),
                "errors", result.errors()
        ));
    }

    @PostMapping("/run-async")
    public ResponseEntity<Map<String, Object>> runPipelineAsync() {
        boolean started = progressService.startAsync();
        if (!started) {
            return ResponseEntity.ok(Map.of("status", "already_running"));
        }
        return ResponseEntity.ok(Map.of("status", "started"));
    }

    @GetMapping("/progress")
    public ResponseEntity<PipelineProgressService.PipelineStatus> getProgress() {
        return ResponseEntity.ok(progressService.getStatus());
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "Healthy",
                "time_of_last_update", Instant.now().getEpochSecond()
        ));
    }

    @PostMapping("/invocations")
    public ResponseEntity<Map<String, Object>> invocations(@RequestParam(required = false) String prompt) {
        AgentPipelineService.RunResult result = pipelineService.runFullPipeline();
        return ResponseEntity.ok(Map.of(
                "response", String.format("Processed %d sites. Found %d new properties, updated %d.",
                        result.sitesProcessed(), result.newProperties(), result.updatedProperties())
        ));
    }
}
