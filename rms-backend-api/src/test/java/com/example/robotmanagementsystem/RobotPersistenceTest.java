package com.example.robotmanagementsystem;

import com.example.robotmanagementsystem.persistence.RobotRepository;
import com.example.robotmanagementsystem.service.RobotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RobotPersistenceTest {

    @Autowired
    private RobotRepository robotRepository;

    @Autowired
    private RobotService robotService;

    @Test
    void startsWithoutMockRobotsWhenDatabaseStartsEmpty() {
        assertThat(robotRepository.count()).isZero();
        assertThat(robotService.findRobot("robot-001")).isEmpty();
    }
}
