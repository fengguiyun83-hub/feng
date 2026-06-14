package com.example.robotmanagementsystem.persistence;

import com.example.robotmanagementsystem.model.AlertStatus;
import com.example.robotmanagementsystem.model.RobotAlert;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "robot_alerts")
public class RobotAlertEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "robot_id", nullable = false, length = 32)
    private String robotId;

    @Column(name = "avg_temperature", nullable = false)
    private double avgTemperature;

    @Column(name = "window_start", nullable = false)
    private long windowStart;

    @Column(name = "window_end", nullable = false)
    private long windowEnd;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    @Column(name = "alert_time", nullable = false)
    private long alertTime;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertStatus status = AlertStatus.NEW;

    @Column(name = "acknowledged_at")
    private Long acknowledgedAt;

    @Column(name = "acknowledged_by", length = 100)
    private String acknowledgedBy;

    @Column(name = "closed_at")
    private Long closedAt;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "operation_note", columnDefinition = "TEXT")
    private String operationNote;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at")
    private Long updatedAt;

    protected RobotAlertEntity() {
    }

    public RobotAlertEntity(String id,
                            String robotId,
                            double avgTemperature,
                            long windowStart,
                            long windowEnd,
                            long eventCount,
                            long alertTime,
                            String payload) {
        this.id = id;
        this.robotId = robotId;
        this.avgTemperature = avgTemperature;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.eventCount = eventCount;
        this.alertTime = alertTime;
        this.payload = payload;
        this.status = AlertStatus.NEW;
    }

    public static RobotAlertEntity fromAlert(RobotAlert alert) {
        return new RobotAlertEntity(
                alert.id(),
                alert.robotId(),
                alert.avgTemperature(),
                alert.windowStart(),
                alert.windowEnd(),
                alert.eventCount(),
                alert.alertTime(),
                alert.payload());
    }

    @PrePersist
    void onCreate() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void acknowledge(String operator, String note) {
        long now = System.currentTimeMillis();
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedAt = now;
        this.acknowledgedBy = operator;
        this.operationNote = mergeNote(note);
        this.updatedAt = now;
    }

    public void close(String operator, String note) {
        long now = System.currentTimeMillis();
        if (this.acknowledgedAt == null) {
            this.acknowledgedAt = now;
            this.acknowledgedBy = operator;
        }
        this.status = AlertStatus.CLOSED;
        this.closedAt = now;
        this.closedBy = operator;
        this.operationNote = mergeNote(note);
        this.updatedAt = now;
    }

    public RobotAlert toAlert() {
        return new RobotAlert(
                id,
                robotId,
                avgTemperature,
                windowStart,
                windowEnd,
                eventCount,
                alertTime,
                payload,
                status,
                acknowledgedAt,
                acknowledgedBy,
                closedAt,
                closedBy,
                operationNote,
                updatedAt);
    }

    private String mergeNote(String note) {
        String normalized = note == null ? "" : note.trim();
        if (normalized.isBlank()) {
            return operationNote;
        }
        if (operationNote == null || operationNote.isBlank()) {
            return normalized;
        }
        return operationNote + "\n" + normalized;
    }
}
