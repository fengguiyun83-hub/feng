import math
import random
import time
from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class OdometrySample:
    robot_id: str
    ros_stamp_millis: int
    position_x: float
    position_y: float
    position_z: float
    yaw_radians: float
    linear_velocity: float
    angular_velocity: float


@dataclass(frozen=True)
class RobotMapping:
    robot_id: str
    odom_topic: str


def parse_robot_mappings(value: str | None) -> list[RobotMapping]:
    if value is None or value.strip() == "":
        return []

    mappings: list[RobotMapping] = []
    for item in value.split(","):
        entry = item.strip()
        if not entry:
            continue
        if ":" not in entry:
            raise ValueError(f"Invalid robot mapping '{entry}'. Expected robot-id:/namespace/odom")
        robot_id, odom_topic = entry.split(":", 1)
        robot_id = robot_id.strip()
        odom_topic = odom_topic.strip()
        if not robot_id or not odom_topic:
            raise ValueError(f"Invalid robot mapping '{entry}'. Robot id and odom topic are required")
        if not odom_topic.startswith("/"):
            odom_topic = f"/{odom_topic}"
        mappings.append(RobotMapping(robot_id=robot_id, odom_topic=odom_topic))
    return mappings


class FaultSimulator:
    def __init__(self, seed: int | None = None, fault_probability: float = 0.02) -> None:
        self._random = random.Random(seed)
        self._fault_probability = fault_probability

    def telemetry_values(self) -> tuple[float, float, float]:
        fault = self._random.random() < self._fault_probability
        if fault:
            return (
                round(self._random.uniform(45.0, 52.0), 2),
                round(self._random.uniform(8.0, 20.0), 2),
                round(self._random.uniform(8.0, 12.0), 2),
            )
        return (
            round(self._random.uniform(39.0, 43.5), 2),
            round(self._random.uniform(72.0, 96.0), 2),
            round(self._random.uniform(2.0, 5.0), 2),
        )


def quaternion_to_yaw(x: float, y: float, z: float, w: float) -> float:
    siny_cosp = 2.0 * (w * z + x * y)
    cosy_cosp = 1.0 - 2.0 * (y * y + z * z)
    return math.atan2(siny_cosp, cosy_cosp)


def odometry_to_telemetry(
    sample: OdometrySample,
    fault_simulator: FaultSimulator,
    bridge_sent_at_millis: int | None = None,
) -> dict[str, Any]:
    sent_at = bridge_sent_at_millis if bridge_sent_at_millis is not None else int(time.time() * 1000)
    temperature, battery, motor_current = fault_simulator.telemetry_values()
    return {
        "schemaVersion": "rms.telemetry.v2",
        "source": "ros2-gazebo",
        "robotId": sample.robot_id,
        "timestamp": sample.ros_stamp_millis or sent_at,
        "rosStampMillis": sample.ros_stamp_millis,
        "bridgeSentAtMillis": sent_at,
        "temperatureCelsius": temperature,
        "batteryPercent": battery,
        "motorCurrentAmp": motor_current,
        "positionX": round(sample.position_x, 4),
        "positionY": round(sample.position_y, 4),
        "positionZ": round(sample.position_z, 4),
        "yawRadians": round(sample.yaw_radians, 4),
        "linearVelocity": round(sample.linear_velocity, 4),
        "angularVelocity": round(sample.angular_velocity, 4),
    }
