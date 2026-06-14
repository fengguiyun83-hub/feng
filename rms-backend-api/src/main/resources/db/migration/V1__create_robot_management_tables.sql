CREATE TABLE robots (
    robot_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    model VARCHAR(50) NOT NULL,
    location VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    battery_percent DOUBLE PRECISION NOT NULL,
    temperature_celsius DOUBLE PRECISION NOT NULL,
    last_seen_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE robot_alerts (
    id VARCHAR(64) PRIMARY KEY,
    robot_id VARCHAR(32) NOT NULL,
    avg_temperature DOUBLE PRECISION NOT NULL,
    window_start BIGINT NOT NULL,
    window_end BIGINT NOT NULL,
    event_count BIGINT NOT NULL,
    alert_time BIGINT NOT NULL,
    payload TEXT NOT NULL,
    created_at BIGINT NOT NULL
);

CREATE INDEX idx_robot_alerts_robot_time ON robot_alerts (robot_id, alert_time DESC);
CREATE INDEX idx_robot_alerts_alert_time ON robot_alerts (alert_time DESC);
