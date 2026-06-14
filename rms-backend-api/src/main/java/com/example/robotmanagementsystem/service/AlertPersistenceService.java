package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.api.AlertOperationRequest;
import com.example.robotmanagementsystem.model.AlertStatus;
import com.example.robotmanagementsystem.model.RobotAlert;
import com.example.robotmanagementsystem.persistence.RobotAlertEntity;
import com.example.robotmanagementsystem.persistence.RobotAlertRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AlertPersistenceService {

    private static final String DEFAULT_OPERATOR = "运维人员";

    private final RedisAlertService redisAlertService;
    private final RobotAlertRepository robotAlertRepository;

    public AlertPersistenceService(RedisAlertService redisAlertService,
                                   RobotAlertRepository robotAlertRepository) {
        this.redisAlertService = redisAlertService;
        this.robotAlertRepository = robotAlertRepository;
    }

    @Transactional
    public List<RobotAlert> findRecentAlerts(int requestedLimit, AlertStatus status) {
        int limit = clampLimit(requestedLimit);
        synchronizeRecentAlerts(limit);
        if (status == null) {
            return robotAlertRepository.findByOrderByAlertTimeDescIdDesc(PageRequest.of(0, limit)).stream()
                    .map(RobotAlertEntity::toAlert)
                    .toList();
        }
        return robotAlertRepository.findByStatusOrderByAlertTimeDescIdDesc(status, PageRequest.of(0, limit)).stream()
                .map(RobotAlertEntity::toAlert)
                .toList();
    }

    @Transactional
    public List<RobotAlert> findRecentAlertsForRobot(String robotId, int requestedLimit, AlertStatus status) {
        int limit = clampLimit(requestedLimit);
        synchronizeRecentAlerts(Math.max(limit, 50));
        if (status == null) {
            return robotAlertRepository.findByRobotIdOrderByAlertTimeDescIdDesc(robotId, PageRequest.of(0, limit)).stream()
                    .map(RobotAlertEntity::toAlert)
                    .toList();
        }
        return robotAlertRepository.findByRobotIdAndStatusOrderByAlertTimeDescIdDesc(robotId, status, PageRequest.of(0, limit)).stream()
                .map(RobotAlertEntity::toAlert)
                .toList();
    }

    @Transactional
    public long countAlerts() {
        synchronizeRecentAlerts(50);
        return robotAlertRepository.count();
    }

    @Transactional
    public long countOpenAlerts() {
        synchronizeRecentAlerts(50);
        return robotAlertRepository.countByStatus(AlertStatus.NEW);
    }

    @Transactional
    public int synchronizeRecentAlerts(int requestedLimit) {
        return upsertAlerts(redisAlertService.findRecentAlerts(requestedLimit));
    }

    @Transactional
    public int upsertAlerts(List<RobotAlert> alerts) {
        int saved = 0;
        for (RobotAlert alert : alerts) {
            if (alert.id() == null || alert.id().isBlank() || robotAlertRepository.existsById(alert.id())) {
                continue;
            }
            robotAlertRepository.save(RobotAlertEntity.fromAlert(alert));
            saved++;
        }
        return saved;
    }

    @Transactional
    public RobotAlert acknowledge(String alertId, AlertOperationRequest request) {
        RobotAlertEntity entity = findEntity(alertId);
        if (entity.getStatus() == AlertStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed alerts cannot be acknowledged.");
        }
        if (entity.getStatus() == AlertStatus.NEW) {
            entity.acknowledge(operator(request), request == null ? null : request.note());
        }
        return entity.toAlert();
    }

    @Transactional
    public RobotAlert close(String alertId, AlertOperationRequest request) {
        RobotAlertEntity entity = findEntity(alertId);
        if (entity.getStatus() == AlertStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Alert is already closed.");
        }
        String note = request == null ? null : request.note();
        if (note == null || note.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Close note is required.");
        }
        entity.close(operator(request), note);
        return entity.toAlert();
    }

    private RobotAlertEntity findEntity(String alertId) {
        return robotAlertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found: " + alertId));
    }

    private String operator(AlertOperationRequest request) {
        if (request == null || request.operator() == null || request.operator().trim().isBlank()) {
            return DEFAULT_OPERATOR;
        }
        return request.operator().trim();
    }

    private int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 50;
        }
        return Math.min(requestedLimit, 200);
    }
}
