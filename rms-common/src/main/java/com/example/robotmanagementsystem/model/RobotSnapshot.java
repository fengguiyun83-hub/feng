package com.example.robotmanagementsystem.model;

public record RobotSnapshot(
        String robotId,
        String name,
        String model,
        String location,
        RobotStatus status,
        double batteryPercent,
        double temperatureCelsius,
        long lastSeenAt,
        double positionX,
        double positionY,
        double positionZ,
        double yawRadians,
        double linearVelocity,
        double angularVelocity,
        double motorCurrentAmp,
        String telemetrySource
) {
}
