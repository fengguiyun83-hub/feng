package com.example.robotmanagementsystem.model;

public record RobotAlert(
        String id,
        String robotId,
        double avgTemperature,
        long windowStart,
        long windowEnd,
        long eventCount,
        long alertTime,
        String payload,
        AlertStatus status,
        Long acknowledgedAt,
        String acknowledgedBy,
        Long closedAt,
        String closedBy,
        String operationNote,
        Long updatedAt
) {
    public RobotAlert(String id,
                      String robotId,
                      double avgTemperature,
                      long windowStart,
                      long windowEnd,
                      long eventCount,
                      long alertTime,
                      String payload) {
        this(id, robotId, avgTemperature, windowStart, windowEnd, eventCount, alertTime, payload,
                AlertStatus.NEW, null, null, null, null, null, null);
    }
}
