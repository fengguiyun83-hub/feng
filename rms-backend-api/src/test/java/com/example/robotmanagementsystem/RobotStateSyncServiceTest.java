package com.example.robotmanagementsystem;

import com.example.robotmanagementsystem.model.RobotTelemetryState;
import com.example.robotmanagementsystem.persistence.RobotRepository;
import com.example.robotmanagementsystem.service.RedisRobotStateService;
import com.example.robotmanagementsystem.service.RobotStateSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class RobotStateSyncServiceTest {

    @Autowired
    private RobotStateSyncService robotStateSyncService;

    @Autowired
    private RobotRepository robotRepository;

    @MockBean
    private RedisRobotStateService redisRobotStateService;

    @BeforeEach
    void cleanRobots() {
        robotRepository.deleteAll();
    }

    @Test
    void registersLatestRobotStateIntoStringPrimaryKey() {
        long timestamp = 1780963200000L;
        when(redisRobotStateService.findRecentStates(100)).thenReturn(List.of(new RobotTelemetryState(
                "1780963200000-0",
                "robot-001",
                timestamp,
                timestamp - 10,
                timestamp,
                43.2,
                82.5,
                3.7,
                1.25,
                -0.4,
                0.0,
                1.57,
                0.22,
                0.05,
                "ros2-gazebo",
                "{}")));

        int updated = robotStateSyncService.synchronizeRecentStates();
        var robot = robotRepository.findById("robot-001").orElseThrow().toSnapshot();

        assertThat(updated).isEqualTo(1);
        assertThat(robot.robotId()).isEqualTo("robot-001");
        assertThat(robot.name()).isEqualTo("ROS2 Robot robot-001");
        assertThat(robot.model()).isEqualTo("AGV-SIM");
        assertThat(robot.location()).isEqualTo("ROS2 Workshop");
        assertThat(robot.positionX()).isEqualTo(1.25);
        assertThat(robot.positionY()).isEqualTo(-0.4);
        assertThat(robot.linearVelocity()).isEqualTo(0.22);
        assertThat(robot.telemetrySource()).isEqualTo("ros2-gazebo");
        assertThat(robot.lastSeenAt()).isEqualTo(timestamp);
    }

    @Test
    void ignoresUnknownSourceAndDoesNotOverwriteRosState() {
        long rosTimestamp = 1780963200000L;
        when(redisRobotStateService.findRecentStates(100)).thenReturn(List.of(new RobotTelemetryState(
                "1780963200000-0",
                "robot-001",
                rosTimestamp,
                rosTimestamp - 10,
                rosTimestamp,
                43.2,
                82.5,
                3.7,
                1.25,
                -0.4,
                0.0,
                1.57,
                0.22,
                0.05,
                "ros2-gazebo",
                "{}")));

        assertThat(robotStateSyncService.synchronizeRecentStates()).isEqualTo(1);

        long unknownTimestamp = rosTimestamp + 1000;
        when(redisRobotStateService.findRecentStates(100)).thenReturn(List.of(
                new RobotTelemetryState(
                        "1780963201000-0",
                        "robot-001",
                        unknownTimestamp,
                        null,
                        null,
                        39.0,
                        90.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        "unknown",
                        "{}")));

        assertThat(robotStateSyncService.synchronizeRecentStates()).isZero();
        var robot = robotRepository.findById("robot-001").orElseThrow().toSnapshot();

        assertThat(robot.positionX()).isEqualTo(1.25);
        assertThat(robot.positionY()).isEqualTo(-0.4);
        assertThat(robot.telemetrySource()).isEqualTo("ros2-gazebo");
        assertThat(robot.lastSeenAt()).isEqualTo(rosTimestamp);
    }

    @Test
    void skipsUnknownSourceBeforeRobotDeduplication() {
        long timestamp = 1780963200000L;
        when(redisRobotStateService.findRecentStates(100)).thenReturn(List.of(
                new RobotTelemetryState(
                        "1780963201000-0",
                        "robot-001",
                        timestamp + 1000,
                        null,
                        null,
                        39.0,
                        90.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        "unknown",
                        "{}"),
                new RobotTelemetryState(
                        "1780963200000-0",
                        "robot-001",
                        timestamp,
                        timestamp - 10,
                        timestamp,
                        43.2,
                        82.5,
                        3.7,
                        1.25,
                        -0.4,
                        0.0,
                        1.57,
                        0.22,
                        0.05,
                        "ros2-gazebo",
                        "{}")));

        assertThat(robotStateSyncService.synchronizeRecentStates()).isEqualTo(1);
        var robot = robotRepository.findById("robot-001").orElseThrow().toSnapshot();

        assertThat(robot.positionX()).isEqualTo(1.25);
        assertThat(robot.telemetrySource()).isEqualTo("ros2-gazebo");
    }
}
