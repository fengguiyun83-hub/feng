package com.example.robotmanagementsystem.flink;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RobotTelemetryJsonParserTest {

    private final RobotTemperatureAlertFlinkJob.RobotTelemetryJsonParser parser =
            new RobotTemperatureAlertFlinkJob.RobotTelemetryJsonParser();

    @Test
    void parsesLegacyTemperatureTelemetry() {
        long now = Instant.parse("2026-06-09T00:00:00Z").toEpochMilli();

        var parsed = parser.parseForTest("""
                {"robotId":"robot-001","temperature":41.5,"timestamp":1780963200000}
                """, now);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().robotId()).isEqualTo("robot-001");
        assertThat(parsed.get().temperature()).isEqualTo(41.5);
        assertThat(parsed.get().positionX()).isZero();
        assertThat(parsed.get().source()).isEqualTo("unknown");
    }

    @Test
    void parsesRosTelemetryV2Fields() {
        long now = Instant.parse("2026-06-09T00:00:00Z").toEpochMilli();

        var parsed = parser.parseForTest("""
                {
                  "schemaVersion":"rms.telemetry.v2",
                  "source":"ros2-gazebo",
                  "robotId":"robot-001",
                  "timestamp":1780963200000,
                  "rosStampMillis":1780963199900,
                  "bridgeSentAtMillis":1780963200010,
                  "temperatureCelsius":43.2,
                  "batteryPercent":82.5,
                  "motorCurrentAmp":3.7,
                  "positionX":1.25,
                  "positionY":-0.4,
                  "positionZ":0.0,
                  "yawRadians":1.57,
                  "linearVelocity":0.22,
                  "angularVelocity":0.05
                }
                """, now);

        assertThat(parsed).isPresent();
        var telemetry = parsed.get();
        assertThat(telemetry.source()).isEqualTo("ros2-gazebo");
        assertThat(telemetry.temperature()).isEqualTo(43.2);
        assertThat(telemetry.batteryPercent()).isEqualTo(82.5);
        assertThat(telemetry.positionX()).isEqualTo(1.25);
        assertThat(telemetry.positionY()).isEqualTo(-0.4);
        assertThat(telemetry.rosStampMillis()).isEqualTo(1780963199900L);
    }

    @Test
    void fallsBackToProcessingTimeWhenWslClockDriftsTooFar() {
        long now = Instant.parse("2026-06-09T00:00:00Z").toEpochMilli();

        var parsed = parser.parseForTest("""
                {"robotId":"robot-001","temperatureCelsius":43.2,"timestamp":1000}
                """, now);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().timestampMillis()).isEqualTo(now);
    }

    @Test
    void requiredSourceFilterDropsLegacyUnknownTelemetry() throws Exception {
        long now = Instant.parse("2026-06-09T00:00:00Z").toEpochMilli();
        var filter = new RobotTemperatureAlertFlinkJob.RequiredSourceFilter("ros2-gazebo");
        var legacy = parser.parseForTest("""
                {"robotId":"robot-001","temperature":41.5,"timestamp":1780963200000}
                """, now).orElseThrow();
        var ros = parser.parseForTest("""
                {"source":"ros2-gazebo","robotId":"robot-001","temperatureCelsius":43.2,"timestamp":1780963200000,"positionX":1.25,"positionY":-0.4}
                """, now).orElseThrow();

        assertThat(filter.filter(legacy)).isFalse();
        assertThat(filter.filter(ros)).isTrue();
    }

    @Test
    void requiredSourceFilterCanBeExplicitlyDisabled() throws Exception {
        long now = Instant.parse("2026-06-09T00:00:00Z").toEpochMilli();
        var filter = new RobotTemperatureAlertFlinkJob.RequiredSourceFilter("any");
        var legacy = parser.parseForTest("""
                {"robotId":"robot-001","temperature":41.5,"timestamp":1780963200000}
                """, now).orElseThrow();

        assertThat(filter.filter(legacy)).isTrue();
    }
}
