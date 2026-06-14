package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.DashboardStreamSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class DashboardStreamService {

    private static final long EMITTER_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long BROADCAST_INTERVAL_SECONDS = 5L;

    private final DashboardService dashboardService;
    private final RobotService robotService;
    private final AlertPersistenceService alertPersistenceService;
    private final SystemStatusService systemStatusService;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "dashboard-sse-broadcaster");
        thread.setDaemon(true);
        return thread;
    });

    public DashboardStreamService(DashboardService dashboardService,
                                  RobotService robotService,
                                  AlertPersistenceService alertPersistenceService,
                                  SystemStatusService systemStatusService) {
        this.dashboardService = dashboardService;
        this.robotService = robotService;
        this.alertPersistenceService = alertPersistenceService;
        this.systemStatusService = systemStatusService;
        this.scheduler.scheduleAtFixedRate(
                this::broadcastSafely,
                BROADCAST_INTERVAL_SECONDS,
                BROADCAST_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public SseEmitter registerEmitter() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(emitter));
        emitter.onTimeout(() -> {
            removeEmitter(emitter);
            emitter.complete();
        });
        emitter.onError(error -> removeEmitter(emitter));

        return emitter;
    }

    public void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }

    public int activeConnectionCount() {
        return emitters.size();
    }

    private void broadcastSafely() {
        if (emitters.isEmpty()) {
            return;
        }

        DashboardStreamSnapshot snapshot = snapshot();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("dashboard")
                        .data(snapshot)
                        .reconnectTime(5000L));
            } catch (IOException | IllegalStateException ex) {
                removeEmitter(emitter);
                emitter.completeWithError(ex);
            }
        }
    }

    private DashboardStreamSnapshot snapshot() {
        return new DashboardStreamSnapshot(
                dashboardService.summary(),
                robotService.findAll(),
                alertPersistenceService.findRecentAlerts(50, null),
                systemStatusService.status(),
                Instant.now().toEpochMilli());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        for (SseEmitter emitter : emitters) {
            emitter.complete();
        }
        emitters.clear();
    }
}
