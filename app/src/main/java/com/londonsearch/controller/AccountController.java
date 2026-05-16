package com.londonsearch.controller;

import com.londonsearch.agent.CostGuard;
import com.londonsearch.model.AlertRecord;
import com.londonsearch.repository.AlertRepository;
import com.londonsearch.repository.MonitoredSiteRepository;
import com.londonsearch.repository.PropertyRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;

@Controller
public class AccountController {

    private final AlertRepository alertRepo;
    private final PropertyRepository propertyRepo;
    private final MonitoredSiteRepository siteRepo;
    private final CostGuard costGuard;

    public AccountController(AlertRepository alertRepo, PropertyRepository propertyRepo,
                              MonitoredSiteRepository siteRepo, CostGuard costGuard) {
        this.alertRepo = alertRepo;
        this.propertyRepo = propertyRepo;
        this.siteRepo = siteRepo;
        this.costGuard = costGuard;
    }

    @GetMapping("/config/account")
    public String account(Model model) {
        List<AlertRecord> alerts = alertRepo.findAll().stream()
                .sorted(Comparator.comparing(
                        (AlertRecord a) -> a.getSentAt() != null ? a.getSentAt() : java.time.Instant.EPOCH)
                        .reversed())
                .toList();

        model.addAttribute("alerts", alerts);
        model.addAttribute("totalProperties", propertyRepo.findAll().size());
        model.addAttribute("newProperties", propertyRepo.findByStatus("new").size());
        model.addAttribute("savedProperties", propertyRepo.findByStatus("saved").size());
        model.addAttribute("enabledSites", siteRepo.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled())).count());
        model.addAttribute("totalSites", siteRepo.findAll().size());
        model.addAttribute("costGuardEnabled", costGuard.isEnabled());
        model.addAttribute("monthlyBudget", costGuard.getMonthlyBudget());

        return "config/account";
    }
}
