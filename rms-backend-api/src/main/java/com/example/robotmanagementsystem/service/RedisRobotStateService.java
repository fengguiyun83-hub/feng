package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.RobotTelemetryState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.resps.StreamEntry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RedisRobotStateService {

    private final String redisHost;
    private final int redisPort;
    private final String stateStreamKey;

    public RedisRobotStateService(
            @Value("${robot.redis.host:localhost}") String redisHost,
            @Value("${robot.redis.port:6379}") int redisPort,
            @Value("${robot.redis.state-stream:robot:states}") String stateStreamKey) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.stateStreamKey = stateStreamKey;
    }

    public List<RobotTelemetryState> findRecentStates(int requestedLimit) {
        int limit = clampLimit(requestedLimit);
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            return jedis.xrevrange(stateStreamKey, "+", "-", limit).stream()
                    .map(this::toState)
                    .toList();
        } catch (JedisException ex) {
            return Collections.emptyList();
        }
    }

    public Optional<RobotTelemetryState> findLatestState() {
        return findRecentStates(1).stream().findFirst();
    }

    public long stateStreamLength() {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            return jedis.xlen(stateStreamKey);
        } catch (JedisException ex) {
            return 0L;
        }
    }

    public boolean isRedisAvailable() {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            return "PONG".equalsIgnoreCase(jedis.ping());
        } catch (JedisException ex) {
            return false;
        }
    }

    public String connectionDescription() {
        return "%s:%d stream=%s".formatted(redisHost, redisPort, stateStreamKey);
    }

    private RobotTelemetryState toState(StreamEntry entry) {
        Map<String, String> fields = entry.getFields();
        return new RobotTelemetryState(
                entry.getID().toString(),
                fields.getOrDefault("robotId", ""),
                parseLong(fields.get("timestamp")),
                parseNullableLong(fields.get("rosStampMillis")),
                parseNullableLong(fields.get("bridgeSentAtMillis")),
                parseDouble(fields.get("temperatureCelsius")),
                parseDouble(fields.get("batteryPercent")),
                parseDouble(fields.get("motorCurrentAmp")),
                parseDouble(fields.get("positionX")),
                parseDouble(fields.get("positionY")),
                parseDouble(fields.get("positionZ")),
                parseDouble(fields.get("yawRadians")),
                parseDouble(fields.get("linearVelocity")),
                parseDouble(fields.get("angularVelocity")),
                fields.getOrDefault("source", "unknown"),
                fields.getOrDefault("payload", ""));
    }

    private int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 50;
        }
        return Math.min(requestedLimit, 500);
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private Long parseNullableLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long parseLong(String value) {
        Long parsed = parseNullableLong(value);
        return parsed == null ? 0L : parsed;
    }
}
