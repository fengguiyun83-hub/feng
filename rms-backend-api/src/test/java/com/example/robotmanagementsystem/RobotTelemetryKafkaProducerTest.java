package com.example.robotmanagementsystem;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Disabled("Deprecated manual-only legacy simulator. Use ROS 2 rms_bridge_node for live telemetry.")
class RobotTelemetryKafkaProducerTest {

    private static final String TOPIC = "robot-telemetry";
    private static final int ROBOT_COUNT = 24;
    private static final int JOINTS_PER_ROBOT = 6;
    private static final int EVENTS_PER_ROBOT = 500;

    @Test
    void sendHighFrequencyJointTemperatureTelemetryToKafka() {
        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProperties());
        try {
            KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
            AtomicLong sequence = new AtomicLong();

            CompletableFuture<?>[] producers = new CompletableFuture<?>[ROBOT_COUNT];
            for (int robotIndex = 0; robotIndex < ROBOT_COUNT; robotIndex++) {
                String robotId = "robot-" + String.format("%03d", robotIndex + 1);
                producers[robotIndex] = CompletableFuture.runAsync(
                        () -> sendRobotTelemetry(kafkaTemplate, robotId, sequence));
            }

            CompletableFuture.allOf(producers).join();
            kafkaTemplate.flush();

            long total = (long) ROBOT_COUNT * EVENTS_PER_ROBOT * JOINTS_PER_ROBOT;
            System.out.printf("Sent %,d telemetry events to Kafka topic %s.%n", total, TOPIC);
        } finally {
            producerFactory.destroy();
        }
    }

    private void sendRobotTelemetry(KafkaTemplate<String, String> kafkaTemplate,
                                    String robotId,
                                    AtomicLong sequence) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int eventIndex = 0; eventIndex < EVENTS_PER_ROBOT; eventIndex++) {
            for (int jointIndex = 1; jointIndex <= JOINTS_PER_ROBOT; jointIndex++) {
                double baseTemperature = 38.0 + jointIndex * 1.7;
                double noise = random.nextGaussian() * 2.5;
                double spike = random.nextDouble() < 0.015 ? random.nextDouble(18.0, 35.0) : 0.0;
                double temperature = baseTemperature + noise + spike;

                String payload = """
                        {"eventId":%d,"robotId":"%s","jointId":"joint-%02d","temperatureCelsius":%.2f,"rpm":%d,"batteryPercent":%.2f,"timestamp":"%s"}
                        """.formatted(
                        sequence.incrementAndGet(),
                        robotId,
                        jointIndex,
                        temperature,
                        random.nextInt(900, 2400),
                        random.nextDouble(35.0, 100.0),
                        Instant.now());

                kafkaTemplate.send(TOPIC, robotId, payload.strip()).join();
            }
        }
    }

    private Map<String, Object> producerProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 64 * 1024);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "robot-telemetry-simulator");
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        return props;
    }
}
