package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.RobotTelemetryState;
import com.example.robotmanagementsystem.model.RobotStatus;
import com.example.robotmanagementsystem.persistence.RobotEntity;
import com.example.robotmanagementsystem.persistence.RobotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RobotStateSyncService {

    private static final int DEFAULT_SYNC_LIMIT = 100;

    private final RedisRobotStateService redisRobotStateService;
    private final RobotRepository robotRepository;
    private final String requiredSource;

    public RobotStateSyncService(RedisRobotStateService redisRobotStateService,
                                 RobotRepository robotRepository,
                                 @Value("${robot.telemetry.required-source:ros2-gazebo}") String requiredSource) {
        this.redisRobotStateService = redisRobotStateService;
        this.robotRepository = robotRepository;
        this.requiredSource = normalizeSource(requiredSource);
    }

    @Transactional
    public int synchronizeRecentStates() {
        List<RobotTelemetryState> states = redisRobotStateService.findRecentStates(DEFAULT_SYNC_LIMIT);
        Set<String> updatedRobots = new HashSet<>();
        int updated = 0;
        for (RobotTelemetryState state : states) {
            if (!isAcceptedSource(state.source())) {
                continue;
            }
            if (state.robotId() == null || state.robotId().isBlank() || !updatedRobots.add(state.robotId())) {
                continue;
            }
            RobotEntity robot = robotRepository.findById(state.robotId())
                    .orElseGet(() -> createRobotFromTelemetry(state));
            robot.applyTelemetry(
                    state.batteryPercent(),
                    state.temperatureCelsius(),
                    state.timestamp(),
                    state.positionX(),
                    state.positionY(),
                    state.positionZ(),
                    state.yawRadians(),
                    state.linearVelocity(),
                    state.angularVelocity(),
                    state.motorCurrentAmp(),
                    state.source());
            robotRepository.save(robot);
            updated++;
        }
        return updated;
    }

    private RobotEntity createRobotFromTelemetry(RobotTelemetryState state) {
        return new RobotEntity(
                state.robotId(),
                "ROS2 Robot " + state.robotId(),
                "AGV-SIM",
                "ROS2 Workshop",
                RobotStatus.ONLINE,
                state.batteryPercent(),
                state.temperatureCelsius(),
                state.timestamp());
    }

    public long stateStreamLength() {
        return redisRobotStateService.stateStreamLength();
    }

    public long latestStateTimestamp() {
        return redisRobotStateService.findLatestState()
                .map(RobotTelemetryState::timestamp)
                .orElse(0L);
    }

    private boolean isAcceptedSource(String source) {
        return requiredSource.isBlank()
                || "any".equalsIgnoreCase(requiredSource)
                || requiredSource.equalsIgnoreCase(normalizeSource(source));
    }

    private String normalizeSource(String source) {
        return source == null ? "" : source.trim();
    }
}
