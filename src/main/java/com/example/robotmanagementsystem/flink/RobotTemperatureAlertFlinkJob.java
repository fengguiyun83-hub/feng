package com.example.robotmanagementsystem.flink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
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
    private static final String DEFAULT_REDIS_STREAM = "robot:alerts";
    private static final double DEFAULT_ALERT_THRESHOLD = 80.0;

    public static void main(String[] args) throws Exception {
        ParameterTool params = ParameterTool.fromArgs(args);

        String bootstrapServers = params.get("bootstrap.servers", DEFAULT_BOOTSTRAP_SERVERS);
        String topic = params.get("topic", DEFAULT_TOPIC);
        String groupId = params.get("group.id", DEFAULT_GROUP_ID);
        String redisHost = params.get("redis.host", DEFAULT_REDIS_HOST);
        int redisPort = params.getInt("redis.port", DEFAULT_REDIS_PORT);
        String redisStream = params.get("redis.stream", DEFAULT_REDIS_STREAM);
        double alertThreshold = params.getDouble("alert.threshold", DEFAULT_ALERT_THRESHOLD);

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

        DataStream<TemperatureAlert> alerts = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka robot telemetry source")
                .flatMap(new RobotTelemetryJsonParser())
                .assignTimestampsAndWatermarks(watermarkStrategy)
                .keyBy(RobotTelemetry::robotId)
                .window(SlidingEventTimeWindows.of(Time.seconds(5), Time.seconds(1)))
                .aggregate(new AverageTemperatureAggregate(), new TemperatureAlertWindowFunction())
                .filter((FilterFunction<TemperatureAlert>) alert -> alert.avgTemperature() > alertThreshold);

        alerts.map(TemperatureAlert::toJson).name("Print robot temperature alerts").print();
        alerts.addSink(new RedisStreamAlertSink(redisHost, redisPort, redisStream))
                .name("Redis stream robot temperature alerts");

        env.execute("Robot Temperature Alert Flink Job");
    }

    public record RobotTelemetry(String robotId, double temperature, long timestampMillis) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
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
            fields.put("avgTemperature", String.format("%.2f", avgTemperature));
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

        @Override
        public void flatMap(String value, Collector<RobotTelemetry> out) {
            parse(value).ifPresent(out::collect);
        }

        private Optional<RobotTelemetry> parse(String value) {
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
                long timestampMillis = parseTimestampMillis(timestampNode);

                return Optional.of(new RobotTelemetry(robotId, temperature, timestampMillis));
            } catch (Exception ex) {
                System.err.printf("Drop invalid telemetry JSON: %s, reason: %s%n", value, ex.getMessage());
                return Optional.empty();
            }
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
}
