package com.example.robotmanagementsystem.model;

import java.util.List;

public record DashboardStreamSnapshot(
        DashboardSummary summary,
        List<RobotSnapshot> robots,
        List<RobotAlert> alerts,
        SystemStatus system,
        long timestamp
) {
}
