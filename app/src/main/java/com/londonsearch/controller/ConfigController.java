package com.londonsearch.controller;

import com.londonsearch.model.MonitoredSite;
import com.londonsearch.model.SearchConfig;
import com.londonsearch.repository.MonitoredSiteRepository;
import com.londonsearch.repository.SearchConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/config")
public class ConfigController {

    private final SearchConfigRepository searchConfigRepository;
    private final MonitoredSiteRepository monitoredSiteRepository;

    public ConfigController(SearchConfigRepository searchConfigRepository,
                            MonitoredSiteRepository monitoredSiteRepository) {
        this.searchConfigRepository = searchConfigRepository;
        this.monitoredSiteRepository = monitoredSiteRepository;
    }

    // ── Search Criteria ──────────────────────────────────────────────────────

    @GetMapping({"/search", ""})
    public String searchConfig(@RequestParam(required = false) String edit, Model model) {
        List<SearchConfig> configs = searchConfigRepository.findAll();
        model.addAttribute("configs", configs);
        model.addAttribute("editId", edit);

        if (edit != null && !"new".equals(edit)) {
            searchConfigRepository.findById(edit).ifPresent(c -> model.addAttribute("editConfig", c));
        }

        return "config/search";
    }

    @PostMapping("/search/save")
    public String saveSearchConfig(
            @RequestParam(required = false) String id,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "") String areas,
            @RequestParam(required = false) Integer minBeds,
            @RequestParam(required = false) Integer maxBeds,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer minBaths,
            @RequestParam(required = false) List<String> furnishing,
            @RequestParam(required = false, defaultValue = "") String additionalCriteria) {

        SearchConfig config;
        if (id != null && !id.isBlank()) {
            config = searchConfigRepository.findById(id)
                    .orElse(new SearchConfig());
        } else {
            config = new SearchConfig();
            config.setId(UUID.randomUUID().toString());
            config.setCreatedAt(Instant.now());
            config.setEnabled(true);
        }

        config.setName(name);

        List<String> areaList = Arrays.stream(areas.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        config.setAreas(areaList);

        config.setMinBeds(minBeds);
        config.setMaxBeds(maxBeds);
        config.setMinPrice(minPrice);
        config.setMaxPrice(maxPrice);
        config.setMinBaths(minBaths);
        config.setFurnishing(furnishing != null ? furnishing : List.of());
        config.setAdditionalCriteria(additionalCriteria);

        searchConfigRepository.save(config);
        return "redirect:/config/search";
    }

    @PostMapping("/search/{id}/toggle")
    public String toggleSearchConfig(@PathVariable String id) {
        SearchConfig config = searchConfigRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SearchConfig not found"));
        config.setEnabled(config.getEnabled() == null || !config.getEnabled());
        searchConfigRepository.save(config);
        return "redirect:/config/search";
    }

    @PostMapping("/search/{id}/delete")
    public String deleteSearchConfig(@PathVariable String id) {
        searchConfigRepository.delete(id);
        return "redirect:/config/search";
    }

    // ── Monitored Sites ───────────────────────────────────────────────────────

    @GetMapping("/sites")
    public String monitoredSites(Model model) {
        List<MonitoredSite> sites = monitoredSiteRepository.findAll();
        model.addAttribute("sites", sites);
        return "config/sites";
    }

    @PostMapping("/sites/add")
    public String addSite(
            @RequestParam String name,
            @RequestParam String baseUrl,
            @RequestParam String scraperType) {

        MonitoredSite site = new MonitoredSite();
        site.setId(UUID.randomUUID().toString());
        site.setName(name);
        site.setBaseUrl(baseUrl);
        site.setScraperType(scraperType);
        site.setEnabled(true);
        monitoredSiteRepository.save(site);
        return "redirect:/config/sites";
    }

    @PostMapping("/sites/{id}/toggle")
    public String toggleSite(@PathVariable String id) {
        MonitoredSite site = monitoredSiteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MonitoredSite not found"));
        site.setEnabled(site.getEnabled() == null || !site.getEnabled());
        monitoredSiteRepository.save(site);
        return "redirect:/config/sites";
    }

    @PostMapping("/sites/{id}/delete")
    public String deleteSite(@PathVariable String id) {
        monitoredSiteRepository.delete(id);
        return "redirect:/config/sites";
    }
}
