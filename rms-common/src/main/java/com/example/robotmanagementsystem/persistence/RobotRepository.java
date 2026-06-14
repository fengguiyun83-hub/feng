package com.example.robotmanagementsystem.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<RobotEntity, String> {
}
