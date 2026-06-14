package com.example.robotmanagementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.example.robotmanagementsystem.persistence")
public class RobotManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotManagementSystemApplication.class, args);
    }
}
