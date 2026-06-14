ALTER TABLE robot_alerts ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'NEW';
ALTER TABLE robot_alerts ADD COLUMN acknowledged_at BIGINT;
ALTER TABLE robot_alerts ADD COLUMN acknowledged_by VARCHAR(100);
ALTER TABLE robot_alerts ADD COLUMN closed_at BIGINT;
ALTER TABLE robot_alerts ADD COLUMN closed_by VARCHAR(100);
ALTER TABLE robot_alerts ADD COLUMN operation_note TEXT;
ALTER TABLE robot_alerts ADD COLUMN updated_at BIGINT;

UPDATE robot_alerts
SET status = 'NEW',
    updated_at = created_at
WHERE updated_at IS NULL;

CREATE INDEX idx_robot_alerts_status_time ON robot_alerts (status, alert_time DESC);
