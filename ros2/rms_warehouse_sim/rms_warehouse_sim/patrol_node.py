import time

import rclpy
from geometry_msgs.msg import Twist
from rclpy.node import Node

from rms_warehouse_sim.patrol_logic import ROBOT_IDS, cmd_vel_topic, command_for_elapsed


class RmsPatrolNode(Node):
    def __init__(self) -> None:
        super().__init__("rms_patrol_node")
        self.declare_parameter("robot_ids", ",".join(ROBOT_IDS))
        robot_ids_text = self.get_parameter("robot_ids").get_parameter_value().string_value
        self.robot_ids = [item.strip() for item in robot_ids_text.split(",") if item.strip()]
        self._cmd_vel_publishers = [
            self.create_publisher(Twist, cmd_vel_topic(robot_id), 10)
            for robot_id in self.robot_ids
        ]
        self.started_at = time.monotonic()
        self.timer = self.create_timer(0.2, self.publish_commands)
        self.get_logger().info("RMS patrol node started for " + ", ".join(self.robot_ids))

    def publish_commands(self) -> None:
        elapsed = time.monotonic() - self.started_at
        for index, publisher in enumerate(self._cmd_vel_publishers):
            command = command_for_elapsed(elapsed, index)
            message = Twist()
            message.linear.x = command.linear_x
            message.angular.z = command.angular_z
            publisher.publish(message)


def main(args: list[str] | None = None) -> None:
    rclpy.init(args=args)
    node = RmsPatrolNode()
    try:
        rclpy.spin(node)
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == "__main__":
    main()
