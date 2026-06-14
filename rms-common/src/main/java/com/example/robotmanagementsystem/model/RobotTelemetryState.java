package com.example.robotmanagementsystem.model;

public record RobotTelemetryState(
        String id,
        String robotId,
        long timestamp,
        Long rosStampMillis,
        Long bridgeSentAtMillis,
        double temperatureCelsius,
        double batteryPercent,
        double motorCurrentAmp,
        double positionX,
        double positionY,
        double positionZ,
        double yawRadians,
        double linearVelocity,
        double angularVelocity,
        String source,
        String payload
) {
}
