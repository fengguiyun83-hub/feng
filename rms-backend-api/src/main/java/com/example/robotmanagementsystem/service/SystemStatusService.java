package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.ComponentStatus;
import com.example.robotmanagementsystem.model.SystemStatus;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Service
public class SystemStatusService {

    private final RedisAlertService redisAlertService;
    private final RobotStateSyncService robotStateSyncService;
    private final DataSource dataSource;

    public SystemStatusService(RedisAlertService redisAlertService,
                               RobotStateSyncService robotStateSyncService,
                               DataSource dataSource) {
        this.redisAlertService = redisAlertService;
        this.robotStateSyncService = robotStateSyncService;
        this.dataSource = dataSource;
    }

    public SystemStatus status() {
        boolean redisAvailable = redisAlertService.isRedisAvailable();
        boolean postgresAvailable = isPostgresAvailable();
        ComponentStatus bridgeStatus = ros2BridgeStatus();
        List<ComponentStatus> components = List.of(
                new ComponentStatus("backend", "UP", "Spring Boot API is running"),
                new ComponentStatus("postgres", postgresAvailable ? "UP" : "DOWN", "jdbc:postgresql://localhost:5432/robot_management"),
                new ComponentStatus("redis", redisAvailable ? "UP" : "DOWN", redisAlertService.connectionDescription()),
                bridgeStatus,
                new ComponentStatus("kafka", "CONFIGURED", "localhost:9092 for host, kafka:19092 inside Docker network"),
                new ComponentStatus("flink", "CONFIGURED", "JobManager UI expected at http://localhost:8081"));

        String overall = redisAvailable && postgresAvailable ? "UP" : "DEGRADED";
        return new SystemStatus(overall, components);
    }

    private boolean isPostgresAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private ComponentStatus ros2BridgeStatus() {
        long stateCount = robotStateSyncService.stateStreamLength();
        long latestTimestamp = robotStateSyncService.latestStateTimestamp();
        if (stateCount <= 0 || latestTimestamp <= 0) {
            return new ComponentStatus("ros2_bridge", "NO_DATA", "No telemetry in Redis stream robot:states yet");
        }
        long ageMillis = Math.max(0L, System.currentTimeMillis() - latestTimestamp);
        if (ageMillis <= 10_000L) {
            return new ComponentStatus("ros2_bridge", "UP", "Latest robot state is %d ms old".formatted(ageMillis));
        }
        return new ComponentStatus("ros2_bridge", "STALE", "Latest robot state is %d ms old".formatted(ageMillis));
    }
}
