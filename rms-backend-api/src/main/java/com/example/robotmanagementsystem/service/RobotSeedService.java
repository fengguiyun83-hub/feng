package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.RobotStatus;
import com.example.robotmanagementsystem.persistence.RobotEntity;
import com.example.robotmanagementsystem.persistence.RobotRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

@Component
@ConditionalOnProperty(name = "robot.seed.enabled", havingValue = "true")
public class RobotSeedService implements ApplicationRunner {

    private final RobotRepository robotRepository;

    public RobotSeedService(RobotRepository robotRepository) {
        this.robotRepository = robotRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (robotRepository.count() > 0) {
            return;
        }
        robotRepository.saveAll(createRobots());
    }

    private List<RobotEntity> createRobots() {
        long now = Instant.now().toEpochMilli();
        return IntStream.rangeClosed(1, 24)
                .mapToObj(index -> {
                    String robotId = "robot-%03d".formatted(index);
                    RobotStatus status = switch (index % 8) {
                        case 0 -> RobotStatus.MAINTENANCE;
                        case 3, 6 -> RobotStatus.WARNING;
                        case 5 -> RobotStatus.OFFLINE;
                        default -> RobotStatus.ONLINE;
                    };
                    double battery = Math.max(18.0, 96.0 - index * 2.4);
                    double temperature = 39.0 + (index % 6) * 1.7 + (status == RobotStatus.WARNING ? 4.5 : 0.0);
                    long lastSeen = status == RobotStatus.OFFLINE ? now - 30 * 60 * 1000L : now - index * 15_000L;

                    return new RobotEntity(
                            robotId,
                            "巡检机器人-%02d".formatted(index),
                            index % 3 == 0 ? "RX-200" : "RX-100",
                            "产线-%d / 工位-%02d".formatted((index - 1) / 6 + 1, index),
                            status,
                            round(battery),
                            round(temperature),
                            lastSeen);
                })
                .toList();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
