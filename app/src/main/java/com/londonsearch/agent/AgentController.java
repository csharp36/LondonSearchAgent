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

    public AgentController(AgentPipelineService pipelineService) {
        this.pipelineService = pipelineService;
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
