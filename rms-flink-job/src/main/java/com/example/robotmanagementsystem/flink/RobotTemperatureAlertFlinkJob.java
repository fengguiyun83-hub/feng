package com.example.robotmanagementsystem.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.XAddParams;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class RobotTemperatureAlertFlinkJob {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEFAULT_TOPIC = "robot-telemetry";
    private static final String DEFAULT_GROUP_ID = "robot-temperature-alert-flink";
    private static final String DEFAULT_REDIS_HOST = "localhost";
    private static final int DEFAULT_REDIS_PORT = 6379;
    private static final String DEFAULT_REDIS_ALERT_STREAM = "robot:alerts";
    private static final String DEFAULT_REDIS_STATE_STREAM = "robot:states";
    private static final double DEFAULT_ALERT_THRESHOLD = 80.0;
    private static final long DEFAULT_STATE_EMIT_INTERVAL_MILLIS = 200L;
    private static final String DEFAULT_REQUIRED_SOURCE = "ros2-gazebo";

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);

        String bootstrapServers = params.get("bootstrap.servers", DEFAULT_BOOTSTRAP_SERVERS);
        String topic = params.get("topic", DEFAULT_TOPIC);
        String groupId = params.get("group.id", DEFAULT_GROUP_ID);
        String redisHost = params.get("redis.host", DEFAULT_REDIS_HOST);
        int redisPort = params.getInt("redis.port", DEFAULT_REDIS_PORT);
        String redisAlertStream = params.get("redis.stream", DEFAULT_REDIS_ALERT_STREAM);
        String redisStateStream = params.get("redis.state-stream", DEFAULT_REDIS_STATE_STREAM);
        double alertThreshold = params.getDouble("alert.threshold", DEFAULT_ALERT_THRESHOLD);
        long stateEmitIntervalMillis = params.getLong("state.emit-interval-ms", DEFAULT_STATE_EMIT_INTERVAL_MILLIS);
        String requiredSource = params.get("telemetry.required-source", DEFAULT_REQUIRED_SOURCE);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.getConfig().setGlobalJobParameters(params);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(topic)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        WatermarkStrategy<RobotTelemetry> watermarkStrategy = WatermarkStrategy
                .<RobotTelemetry>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                .withTimestampAssigner((SerializableTimestampAssigner<RobotTelemetry>)
                        (event, recordTimestamp) -> event.timestampMillis());

        DataStream<RobotTelemetry> telemetry = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka robot telemetry source")
                .flatMap(new RobotTelemetryJsonParser())
                .name("Parse robot telemetry");

        DataStream<RobotTelemetry> rosTelemetry = telemetry
                .filter(new RequiredSourceFilter(requiredSource))
                .name("Filter telemetry by source");

        DataStream<RobotTelemetry> timedTelemetry = rosTelemetry
                .assignTimestampsAndWatermarks(watermarkStrategy);

        DataStream<TemperatureAlert> alerts = timedTelemetry
                .keyBy(RobotTelemetry::robotId)
                .window(SlidingEventTimeWindows.of(Time.seconds(5), Time.seconds(1)))
                .aggregate(new AverageTemperatureAggregate(), new TemperatureAlertWindowFunction())
                .filter((FilterFunction<TemperatureAlert>) alert -> alert.avgTemperature() > alertThreshold);

        alerts.map(TemperatureAlert::toJson).name("Print robot temperature alerts").print();
        alerts.addSink(new RedisStreamAlertSink(redisHost, redisPort, redisAlertStream))
                .name("Redis stream robot temperature alerts");

        timedTelemetry
                .keyBy(RobotTelemetry::robotId)
                .flatMap(new StateRateLimitFunction(stateEmitIntervalMillis))
                .name("Rate limit robot state stream")
                .addSink(new RedisStreamStateSink(redisHost, redisPort, redisStateStream))
                .name("Redis stream robot digital twin states");

        env.execute("Robot Temperature Alert and State Flink Job");
    }

    public record RobotTelemetry(
            String robotId,
            double temperature,
            long timestampMillis,
            Long rosStampMillis,
            Long bridgeSentAtMillis,
            double batteryPercent,
            double motorCurrentAmp,
            double positionX,
            double positionY,
            double positionZ,
            double yawRadians,
            double linearVelocity,
            double angularVelocity,
            String source,
            String payload
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public Map<String, String> toRedisStateFields() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("robotId", robotId);
            fields.put("timestamp", Long.toString(timestampMillis));
            putNullableLong(fields, "rosStampMillis", rosStampMillis);
            putNullableLong(fields, "bridgeSentAtMillis", bridgeSentAtMillis);
            fields.put("temperatureCelsius", formatDouble(temperature));
            fields.put("batteryPercent", formatDouble(batteryPercent));
            fields.put("motorCurrentAmp", formatDouble(motorCurrentAmp));
            fields.put("positionX", formatDouble(positionX));
            fields.put("positionY", formatDouble(positionY));
            fields.put("positionZ", formatDouble(positionZ));
            fields.put("yawRadians", formatDouble(yawRadians));
            fields.put("linearVelocity", formatDouble(linearVelocity));
            fields.put("angularVelocity", formatDouble(angularVelocity));
            fields.put("source", source);
            fields.put("payload", payload);
            return fields;
        }

        private static void putNullableLong(Map<String, String> fields, String key, Long value) {
            if (value != null) {
                fields.put(key, Long.toString(value));
            }
        }
    }

    public record TemperatureAlert(
            String robotId,
            double avgTemperature,
            long windowStart,
            long windowEnd,
            long eventCount,
            long alertTime
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public String toJson() {
            return """
                    {"robotId":"%s","avgTemperature":%.2f,"windowStart":%d,"windowEnd":%d,"eventCount":%d,"alertTime":%d}
                    """.formatted(robotId, avgTemperature, windowStart, windowEnd, eventCount, alertTime).strip();
        }

        public Map<String, String> toRedisFields() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("robotId", robotId);
            fields.put("avgTemperature", formatDouble(avgTemperature));
            fields.put("windowStart", Long.toString(windowStart));
            fields.put("windowEnd", Long.toString(windowEnd));
            fields.put("eventCount", Long.toString(eventCount));
            fields.put("alertTime", Long.toString(alertTime));
            fields.put("payload", toJson());
            return fields;
        }
    }

    public static class RobotTelemetryJsonParser
            implements org.apache.flink.api.common.functions.FlatMapFunction<String, RobotTelemetry> {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
        private static final long MAX_PAST_DRIFT_MILLIS = Duration.ofMinutes(10).toMillis();
        private static final long MAX_FUTURE_DRIFT_MILLIS = Duration.ofMinutes(2).toMillis();

        @Override
        public void flatMap(String value, Collector<RobotTelemetry> out) {
            parse(value, System.currentTimeMillis()).ifPresent(out::collect);
        }

        public Optional<RobotTelemetry> parseForTest(String value, long nowMillis) {
            return parse(value, nowMillis);
        }

        private Optional<RobotTelemetry> parse(String value, long nowMillis) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(value);
                JsonNode robotIdNode = root.get("robotId");
                JsonNode temperatureNode = root.hasNonNull("temperature")
                        ? root.get("temperature")
                        : root.get("temperatureCelsius");
                JsonNode timestampNode = root.get("timestamp");

                if (robotIdNode == null || temperatureNode == null || timestampNode == null) {
                    System.err.printf("Drop telemetry with missing required fields: %s%n", value);
                    return Optional.empty();
                }

                String robotId = robotIdNode.asText();
                double temperature = temperatureNode.asDouble();
                long timestampMillis = sanitizeTimestamp(parseTimestampMillis(timestampNode), nowMillis);

                return Optional.of(new RobotTelemetry(
                        robotId,
                        temperature,
                        timestampMillis,
                        parseOptionalTimestamp(root.get("rosStampMillis")),
                        parseOptionalTimestamp(root.get("bridgeSentAtMillis")),
                        doubleOrDefault(root.get("batteryPercent"), 100.0),
                        doubleOrDefault(root.get("motorCurrentAmp"), 0.0),
                        doubleOrDefault(root.get("positionX"), 0.0),
                        doubleOrDefault(root.get("positionY"), 0.0),
                        doubleOrDefault(root.get("positionZ"), 0.0),
                        doubleOrDefault(root.get("yawRadians"), 0.0),
                        doubleOrDefault(root.get("linearVelocity"), 0.0),
                        doubleOrDefault(root.get("angularVelocity"), 0.0),
                        textOrDefault(root.get("source"), "unknown"),
                        value));
            } catch (Exception ex) {
                System.err.printf("Drop invalid telemetry JSON: %s, reason: %s%n", value, ex.getMessage());
                return Optional.empty();
            }
        }

        private Long parseOptionalTimestamp(JsonNode timestampNode) {
            if (timestampNode == null || timestampNode.isNull()) {
                return null;
            }
            return parseTimestampMillis(timestampNode);
        }

        private long sanitizeTimestamp(long timestampMillis, long nowMillis) {
            if (timestampMillis < nowMillis - MAX_PAST_DRIFT_MILLIS
                    || timestampMillis > nowMillis + MAX_FUTURE_DRIFT_MILLIS) {
                return nowMillis;
            }
            return timestampMillis;
        }

        private long parseTimestampMillis(JsonNode timestampNode) {
            if (timestampNode.isNumber()) {
                long raw = timestampNode.asLong();
                return raw < 10_000_000_000L ? raw * 1000L : raw;
            }

            String text = timestampNode.asText();
            try {
                long raw = Long.parseLong(text);
                return raw < 10_000_000_000L ? raw * 1000L : raw;
            } catch (NumberFormatException ignored) {
                // Fall through to ISO-8601 parsing.
            }

            try {
                return Instant.parse(text).toEpochMilli();
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("timestamp must be Unix seconds, Unix milliseconds, or ISO-8601");
            }
        }

        private double doubleOrDefault(JsonNode node, double defaultValue) {
            return node == null || node.isNull() ? defaultValue : node.asDouble(defaultValue);
        }

        private String textOrDefault(JsonNode node, String defaultValue) {
            if (node == null || node.isNull() || node.asText().isBlank()) {
                return defaultValue;
            }
            return node.asText();
        }
    }

    public static class StateRateLimitFunction extends RichFlatMapFunction<RobotTelemetry, RobotTelemetry> {

        @Serial
        private static final long serialVersionUID = 1L;

        private final long emitIntervalMillis;
        private transient ValueState<Long> lastEmittedAt;

        public StateRateLimitFunction(long emitIntervalMillis) {
            this.emitIntervalMillis = Math.max(1L, emitIntervalMillis);
        }

        @Override
        public void open(Configuration parameters) {
            lastEmittedAt = getRuntimeContext().getState(
                    new ValueStateDescriptor<>("last-state-emitted-at", Long.class));
        }

        @Override
        public void flatMap(RobotTelemetry value, Collector<RobotTelemetry> out) throws Exception {
            long now = System.currentTimeMillis();
            Long last = lastEmittedAt.value();
            if (last == null || now - last >= emitIntervalMillis) {
                lastEmittedAt.update(now);
                out.collect(value);
            }
        }
    }

    public static class RequiredSourceFilter implements FilterFunction<RobotTelemetry> {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String requiredSource;

        public RequiredSourceFilter(String requiredSource) {
            this.requiredSource = normalize(requiredSource);
        }

        @Override
        public boolean filter(RobotTelemetry value) {
            if (requiredSource.isBlank() || "any".equalsIgnoreCase(requiredSource)) {
                return true;
            }
            return requiredSource.equalsIgnoreCase(normalize(value.source()));
        }

        private static String normalize(String source) {
            return source == null ? "" : source.trim();
        }
    }

    public static class AverageTemperatureAggregate
            implements AggregateFunction<RobotTelemetry, TemperatureAccumulator, TemperatureAccumulator> {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public TemperatureAccumulator createAccumulator() {
            return new TemperatureAccumulator(0.0, 0L);
        }

        @Override
        public TemperatureAccumulator add(RobotTelemetry value, TemperatureAccumulator accumulator) {
            return new TemperatureAccumulator(
                    accumulator.temperatureSum() + value.temperature(),
                    accumulator.eventCount() + 1);
        }

        @Override
        public TemperatureAccumulator getResult(TemperatureAccumulator accumulator) {
            return accumulator;
        }

        @Override
        public TemperatureAccumulator merge(TemperatureAccumulator a, TemperatureAccumulator b) {
            return new TemperatureAccumulator(
                    a.temperatureSum() + b.temperatureSum(),
                    a.eventCount() + b.eventCount());
        }
    }

    public record TemperatureAccumulator(double temperatureSum, long eventCount) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        double averageTemperature() {
            return eventCount == 0 ? 0.0 : temperatureSum / eventCount;
        }
    }

    public static class TemperatureAlertWindowFunction
            extends ProcessWindowFunction<TemperatureAccumulator, TemperatureAlert, String, TimeWindow> {

        @Serial
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String robotId,
                            Context context,
                            Iterable<TemperatureAccumulator> elements,
                            Collector<TemperatureAlert> out) {
            TemperatureAccumulator accumulator = elements.iterator().next();
            out.collect(new TemperatureAlert(
                    robotId,
                    accumulator.averageTemperature(),
                    context.window().getStart(),
                    context.window().getEnd(),
                    accumulator.eventCount(),
                    Instant.now().toEpochMilli()));
        }
    }

    public static class RedisStreamAlertSink extends RichSinkFunction<TemperatureAlert> {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String host;
        private final int port;
        private final String streamKey;
        private transient Jedis jedis;

        public RedisStreamAlertSink(String host, int port, String streamKey) {
            this.host = host;
            this.port = port;
            this.streamKey = streamKey;
        }

        @Override
        public void open(Configuration parameters) {
            this.jedis = new Jedis(host, port);
        }

        @Override
        public void invoke(TemperatureAlert value, Context context) {
            jedis.xadd(streamKey, XAddParams.xAddParams(), value.toRedisFields());
        }

        @Override
        public void close() {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public static class RedisStreamStateSink extends RichSinkFunction<RobotTelemetry> {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String host;
        private final int port;
        private final String streamKey;
        private transient Jedis jedis;

        public RedisStreamStateSink(String host, int port, String streamKey) {
            this.host = host;
            this.port = port;
            this.streamKey = streamKey;
        }

        @Override
        public void open(Configuration parameters) {
            this.jedis = new Jedis(host, port);
        }

        @Override
        public void invoke(RobotTelemetry value, Context context) {
            jedis.xadd(streamKey, XAddParams.xAddParams().maxLen(10_000L), value.toRedisStateFields());
        }

        @Override
        public void close() {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    private static String formatDouble(double value) {
        return String.format("%.4f", value);
    }
}
