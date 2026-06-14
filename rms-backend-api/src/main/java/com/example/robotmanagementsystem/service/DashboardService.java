package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.DashboardSummary;
import com.example.robotmanagementsystem.model.RobotSnapshot;
import com.example.robotmanagementsystem.model.RobotStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    private final RobotService robotService;
    private final AlertPersistenceService alertPersistenceService;

    public DashboardService(RobotService robotService, AlertPersistenceService alertPersistenceService) {
        this.robotService = robotService;
        this.alertPersistenceService = alertPersistenceService;
    }

    public DashboardSummary summary() {
        List<RobotSnapshot> robots = robotService.findAll();
        double averageTemperature = robots.stream()
                .mapToDouble(RobotSnapshot::temperatureCelsius)
                .average()
                .orElse(0.0);

        return new DashboardSummary(
                robots.size(),
                countByStatus(robots, RobotStatus.ONLINE),
                countByStatus(robots, RobotStatus.WARNING),
                countByStatus(robots, RobotStatus.MAINTENANCE),
                Math.round(averageTemperature * 100.0) / 100.0,
                alertPersistenceService.countAlerts(),
                alertPersistenceService.countOpenAlerts());
    }

    private long countByStatus(List<RobotSnapshot> robots, RobotStatus status) {
        return robots.stream()
                .filter(robot -> robot.status() == status)
                .count();
    }
}
