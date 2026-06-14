package com.example.robotmanagementsystem;

import com.example.robotmanagementsystem.api.AlertOperationRequest;
import com.example.robotmanagementsystem.model.AlertStatus;
import com.example.robotmanagementsystem.model.RobotAlert;
import com.example.robotmanagementsystem.persistence.RobotAlertRepository;
import com.example.robotmanagementsystem.service.AlertPersistenceService;
import com.example.robotmanagementsystem.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AlertPersistenceServiceTest {

    @Autowired
    private AlertPersistenceService alertPersistenceService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private RobotAlertRepository robotAlertRepository;

    @BeforeEach
    void clearAlerts() {
        robotAlertRepository.deleteAll();
    }

    @Test
    void upsertsRedisAlertByStreamIdWithoutOverwritingLifecycleState() {
        RobotAlert alert = alert("1780737301290-1", "robot-001");

        int firstSaved = alertPersistenceService.upsertAlerts(List.of(alert));
        RobotAlert acknowledged = alertPersistenceService.acknowledge(alert.id(), new AlertOperationRequest("alice", "已接手"));
        int secondSaved = alertPersistenceService.upsertAlerts(List.of(alert));

        assertThat(firstSaved).isEqualTo(1);
        assertThat(secondSaved).isZero();
        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(alertPersistenceService.findRecentAlerts(10, null))
                .hasSize(1)
                .first()
                .extracting(RobotAlert::status)
                .isEqualTo(AlertStatus.ACKNOWLEDGED);
    }

    @Test
    void supportsAcknowledgeAndCloseLifecycle() {
        RobotAlert alert = alert("1780737301290-2", "robot-002");
        alertPersistenceService.upsertAlerts(List.of(alert));

        RobotAlert acknowledged = alertPersistenceService.acknowledge(alert.id(), new AlertOperationRequest("operator", null));
        RobotAlert closed = alertPersistenceService.close(alert.id(), new AlertOperationRequest("operator", "现场降温完成"));

        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(closed.status()).isEqualTo(AlertStatus.CLOSED);
        assertThat(closed.operationNote()).contains("现场降温完成");
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    void closeRequiresNoteAndClosedAlertCannotBeAcknowledgedAgain() {
        RobotAlert alert = alert("1780737301290-3", "robot-003");
        alertPersistenceService.upsertAlerts(List.of(alert));

        assertThatThrownBy(() -> alertPersistenceService.close(alert.id(), new AlertOperationRequest("operator", " ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400 BAD_REQUEST");

        alertPersistenceService.close(alert.id(), new AlertOperationRequest("operator", "已恢复"));

        assertThatThrownBy(() -> alertPersistenceService.acknowledge(alert.id(), new AlertOperationRequest("operator", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409 CONFLICT");
    }

    @Test
    void dashboardOpenAlertCountOnlyCountsNewAlerts() {
        RobotAlert first = alert("1780737301290-4", "robot-004");
        RobotAlert second = alert("1780737301290-5", "robot-005");
        alertPersistenceService.upsertAlerts(List.of(first, second));

        assertThat(dashboardService.summary().openAlertCount()).isEqualTo(2);

        alertPersistenceService.acknowledge(first.id(), new AlertOperationRequest("operator", null));
        alertPersistenceService.close(second.id(), new AlertOperationRequest("operator", "已处理"));

        assertThat(dashboardService.summary().openAlertCount()).isZero();
    }

    private RobotAlert alert(String id, String robotId) {
        return new RobotAlert(
                id,
                robotId,
                44.32,
                1780737294000L,
                1780737299000L,
                299,
                1780737301290L,
                "{\"robotId\":\"%s\"}".formatted(robotId));
    }
}
