package com.example.robotmanagementsystem.api;

import com.example.robotmanagementsystem.model.AlertStatus;
import com.example.robotmanagementsystem.model.RobotAlert;
import com.example.robotmanagementsystem.model.RobotSnapshot;
import com.example.robotmanagementsystem.service.AlertPersistenceService;
import com.example.robotmanagementsystem.service.RobotService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/robots")
public class RobotController {

    private final RobotService robotService;
    private final AlertPersistenceService alertPersistenceService;

    public RobotController(RobotService robotService, AlertPersistenceService alertPersistenceService) {
        this.robotService = robotService;
        this.alertPersistenceService = alertPersistenceService;
    }

    @GetMapping
    public List<RobotSnapshot> robots(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String keyword) {
        return robotService.findRobots(status, keyword);
    }

    @GetMapping("/{robotId}")
    public RobotSnapshot robot(@PathVariable String robotId) {
        return robotService.findRobot(robotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Robot not found: " + robotId));
    }

    @GetMapping("/{robotId}/alerts")
    public List<RobotAlert> robotAlerts(@PathVariable String robotId,
                                        @RequestParam(defaultValue = "50") int limit,
                                        @RequestParam(required = false) AlertStatus status) {
        if (robotService.findRobot(robotId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Robot not found: " + robotId);
        }
        return alertPersistenceService.findRecentAlertsForRobot(robotId, limit, status);
    }
}
