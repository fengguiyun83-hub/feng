import json

import rclpy
from kafka import KafkaProducer
from nav_msgs.msg import Odometry
from rclpy.node import Node

from rms_bridge_node.telemetry import (
    FaultSimulator,
    OdometrySample,
    odometry_to_telemetry,
    parse_robot_mappings,
    quaternion_to_yaw,
)


class RmsBridgeNode(Node):
    def __init__(self) -> None:
        super().__init__("rms_bridge_node")
        self.declare_parameter("robot_id", "robot-001")
        self.declare_parameter("odom_topic", "/odom")
        self.declare_parameter("kafka_bootstrap_servers", "localhost:9092")
        self.declare_parameter("kafka_topic", "robot-telemetry")
        self.declare_parameter("fault_probability", 0.02)
        self.declare_parameter("robot_mappings", "")

        self.robot_id = self.get_parameter("robot_id").get_parameter_value().string_value
        self.kafka_topic = self.get_parameter("kafka_topic").get_parameter_value().string_value
        odom_topic = self.get_parameter("odom_topic").get_parameter_value().string_value
        bootstrap_servers = self.get_parameter("kafka_bootstrap_servers").get_parameter_value().string_value
        fault_probability = self.get_parameter("fault_probability").get_parameter_value().double_value
        robot_mappings_text = self.get_parameter("robot_mappings").get_parameter_value().string_value
        robot_mappings = parse_robot_mappings(robot_mappings_text)
        if not robot_mappings:
            robot_mappings = parse_robot_mappings(f"{self.robot_id}:{odom_topic}")

        self.fault_simulator = FaultSimulator(fault_probability=fault_probability)
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda value: json.dumps(value, ensure_ascii=False).encode("utf-8"),
            retries=5,
            linger_ms=20,
        )
        self._odom_subscriptions = [
            self.create_subscription(
                Odometry,
                mapping.odom_topic,
                lambda message, robot_id=mapping.robot_id: self.on_odometry(robot_id, message),
                10,
            )
            for mapping in robot_mappings
        ]
        self.get_logger().info(
            "RMS bridge started: "
            + ", ".join(f"{mapping.robot_id}<-{mapping.odom_topic}" for mapping in robot_mappings)
            + f", kafka={bootstrap_servers}/{self.kafka_topic}"
        )

    def on_odometry(self, robot_id: str, message: Odometry) -> None:
        stamp = message.header.stamp
        ros_stamp_millis = stamp.sec * 1000 + int(stamp.nanosec / 1_000_000)
        orientation = message.pose.pose.orientation
        sample = OdometrySample(
            robot_id=robot_id,
            ros_stamp_millis=ros_stamp_millis,
            position_x=message.pose.pose.position.x,
            position_y=message.pose.pose.position.y,
            position_z=message.pose.pose.position.z,
            yaw_radians=quaternion_to_yaw(orientation.x, orientation.y, orientation.z, orientation.w),
            linear_velocity=message.twist.twist.linear.x,
            angular_velocity=message.twist.twist.angular.z,
        )
        payload = odometry_to_telemetry(sample, self.fault_simulator)
        try:
            self.producer.send(self.kafka_topic, payload)
        except Exception as exc:
            self.get_logger().warning(f"Failed to send telemetry to Kafka: {exc}")

    def destroy_node(self) -> bool:
        try:
            self.producer.flush(timeout=2)
            self.producer.close(timeout=2)
        finally:
            return super().destroy_node()


def main(args: list[str] | None = None) -> None:
    rclpy.init(args=args)
    node = RmsBridgeNode()
    try:
        rclpy.spin(node)
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == "__main__":
    main()
