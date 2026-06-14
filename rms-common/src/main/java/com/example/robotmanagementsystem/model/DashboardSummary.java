package com.example.robotmanagementsystem.model;

public record DashboardSummary(
        int robotTotal,
        long onlineRobots,
        long warningRobots,
        long maintenanceRobots,
        double averageTemperature,
        long redisAlertCount,
        long openAlertCount
) {
}
