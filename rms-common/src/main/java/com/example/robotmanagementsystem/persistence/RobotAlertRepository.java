package com.example.robotmanagementsystem.persistence;

import com.example.robotmanagementsystem.model.AlertStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RobotAlertRepository extends JpaRepository<RobotAlertEntity, String> {

    List<RobotAlertEntity> findByOrderByAlertTimeDescIdDesc(Pageable pageable);

    List<RobotAlertEntity> findByStatusOrderByAlertTimeDescIdDesc(AlertStatus status, Pageable pageable);

    List<RobotAlertEntity> findByRobotIdOrderByAlertTimeDescIdDesc(String robotId, Pageable pageable);

    List<RobotAlertEntity> findByRobotIdAndStatusOrderByAlertTimeDescIdDesc(String robotId, AlertStatus status, Pageable pageable);

    long countByStatus(AlertStatus status);
}
