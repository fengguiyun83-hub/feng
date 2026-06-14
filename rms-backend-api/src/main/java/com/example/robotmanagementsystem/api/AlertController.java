package com.example.robotmanagementsystem.api;

import com.example.robotmanagementsystem.model.AlertStatus;
import com.example.robotmanagementsystem.model.RobotAlert;
import com.example.robotmanagementsystem.service.AlertPersistenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertPersistenceService alertPersistenceService;

    public AlertController(AlertPersistenceService alertPersistenceService) {
        this.alertPersistenceService = alertPersistenceService;
    }

    @GetMapping
    public List<RobotAlert> alerts(@RequestParam(defaultValue = "50") int limit,
                                   @RequestParam(required = false) AlertStatus status) {
        return alertPersistenceService.findRecentAlerts(limit, status);
    }

    @PostMapping("/{alertId}/acknowledge")
    public RobotAlert acknowledge(@PathVariable String alertId,
                                  @RequestBody(required = false) AlertOperationRequest request) {
        return alertPersistenceService.acknowledge(alertId, request);
    }

    @PostMapping("/{alertId}/close")
    public RobotAlert close(@PathVariable String alertId,
                            @RequestBody AlertOperationRequest request) {
        return alertPersistenceService.close(alertId, request);
    }
}
