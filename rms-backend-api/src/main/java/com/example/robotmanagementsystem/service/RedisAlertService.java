package com.example.robotmanagementsystem.service;

import com.example.robotmanagementsystem.model.RobotAlert;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.resps.StreamEntry;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class RedisAlertService {

    private final String redisHost;
    private final int redisPort;
    private final String streamKey;

    public RedisAlertService(
            @Value("${robot.redis.host:localhost}") String redisHost,
            @Value("${robot.redis.port:6379}") int redisPort,
            @Value("${robot.redis.alert-stream:robot:alerts}") String streamKey) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.streamKey = streamKey;
    }

    public List<RobotAlert> findRecentAlerts(int requestedLimit) {
        int limit = clampLimit(requestedLimit);
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            return jedis.xrevrange(streamKey, "+", "-", limit).stream()
                    .map(this::toAlert)
                    .toList();
        } catch (JedisException ex) {
            return Collections.emptyList();
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
        return "%s:%d stream=%s".formatted(redisHost, redisPort, streamKey);
    }

    private RobotAlert toAlert(StreamEntry entry) {
        Map<String, String> fields = entry.getFields();
        return new RobotAlert(
                entry.getID().toString(),
                fields.getOrDefault("robotId", ""),
                parseDouble(fields.get("avgTemperature")),
                parseLong(fields.get("windowStart")),
                parseLong(fields.get("windowEnd")),
                parseLong(fields.get("eventCount")),
                parseLong(fields.get("alertTime")),
                fields.getOrDefault("payload", ""));
    }

    private int clampLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 50;
        }
        return Math.min(requestedLimit, 200);
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

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
