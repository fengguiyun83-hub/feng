package com.example.robotmanagementsystem.persistence;

import com.example.robotmanagementsystem.model.RobotSnapshot;
import com.example.robotmanagementsystem.model.RobotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "robots")
public class RobotEntity {

    @Id
    @Column(name = "robot_id", length = 32)
    private String robotId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RobotStatus status;

    @Column(name = "battery_percent", nullable = false)
    private double batteryPercent;

    @Column(name = "temperature_celsius", nullable = false)
    private double temperatureCelsius;

    @Column(name = "last_seen_at", nullable = false)
    private long lastSeenAt;

    @Column(name = "position_x", nullable = false)
    private double positionX;

    @Column(name = "position_y", nullable = false)
    private double positionY;

    @Column(name = "position_z", nullable = false)
    private double positionZ;

    @Column(name = "yaw_radians", nullable = false)
    private double yawRadians;

    @Column(name = "linear_velocity", nullable = false)
    private double linearVelocity;

    @Column(name = "angular_velocity", nullable = false)
    private double angularVelocity;

    @Column(name = "motor_current_amp", nullable = false)
    private double motorCurrentAmp;

    @Column(name = "telemetry_source", nullable = false, length = 50)
    private String telemetrySource = "seed";

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    protected RobotEntity() {
    }

    public RobotEntity(String robotId,
                       String name,
                       String model,
                       String location,
                       RobotStatus status,
                       double batteryPercent,
                       double temperatureCelsius,
                       long lastSeenAt) {
        this.robotId = robotId;
        this.name = name;
        this.model = model;
        this.location = location;
        this.status = status;
        this.batteryPercent = batteryPercent;
        this.temperatureCelsius = temperatureCelsius;
        this.lastSeenAt = lastSeenAt;
    }

    @PrePersist
    void onCreate() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = System.currentTimeMillis();
    }

    public String getRobotId() {
        return robotId;
    }

    public void applyTelemetry(double batteryPercent,
                               double temperatureCelsius,
                               long lastSeenAt,
                               double positionX,
                               double positionY,
                               double positionZ,
                               double yawRadians,
                               double linearVelocity,
                               double angularVelocity,
                               double motorCurrentAmp,
                               String telemetrySource) {
        this.batteryPercent = batteryPercent;
        this.temperatureCelsius = temperatureCelsius;
        this.lastSeenAt = lastSeenAt;
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
        this.yawRadians = yawRadians;
        this.linearVelocity = linearVelocity;
        this.angularVelocity = angularVelocity;
        this.motorCurrentAmp = motorCurrentAmp;
        this.telemetrySource = telemetrySource == null || telemetrySource.isBlank() ? "unknown" : telemetrySource;
        this.status = batteryPercent < 15.0 || temperatureCelsius >= 45.0 || motorCurrentAmp >= 8.0
                ? RobotStatus.WARNING
                : RobotStatus.ONLINE;
    }

    public RobotSnapshot toSnapshot() {
        return new RobotSnapshot(
                robotId,
                name,
                model,
                location,
                status,
                batteryPercent,
                temperatureCelsius,
                lastSeenAt,
                positionX,
                positionY,
                positionZ,
                yawRadians,
                linearVelocity,
                angularVelocity,
                motorCurrentAmp,
                telemetrySource);
    }
}
