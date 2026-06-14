package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.RobotSnapshot;
import com.example.robotmanagementsystem.model.RobotStatus;
import com.example.robotmanagementsystem.persistence.RobotEntity;
import com.example.robotmanagementsystem.persistence.RobotRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RobotService {

    private final RobotRepository robotRepository;
    private final RobotStateSyncService robotStateSyncService;

    public RobotService(RobotRepository robotRepository, RobotStateSyncService robotStateSyncService) {
        this.robotRepository = robotRepository;
        this.robotStateSyncService = robotStateSyncService;
    }

    public List<RobotSnapshot> findRobots(String status, String keyword) {
        Optional<RobotStatus> statusFilter = parseStatus(status);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        return findAll().stream()
                .filter(robot -> statusFilter.map(value -> robot.status() == value).orElse(true))
                .filter(robot -> normalizedKeyword.isBlank()
                        || robot.robotId().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || robot.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || robot.location().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .toList();
    }

    public Optional<RobotSnapshot> findRobot(String robotId) {
        robotStateSyncService.synchronizeRecentStates();
        return robotRepository.findById(robotId)
                .map(RobotEntity::toSnapshot);
    }

    public List<RobotSnapshot> findAll() {
        robotStateSyncService.synchronizeRecentStates();
        return robotRepository.findAll(Sort.by("robotId")).stream()
                .map(RobotEntity::toSnapshot)
                .toList();
    }

    private Optional<RobotStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(RobotStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
