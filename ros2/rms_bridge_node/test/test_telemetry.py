from rms_bridge_node.telemetry import (
    FaultSimulator,
    OdometrySample,
    odometry_to_telemetry,
    parse_robot_mappings,
    quaternion_to_yaw,
)


def test_odometry_to_telemetry_uses_existing_robot_id_and_pose_fields():
    sample = OdometrySample(
        robot_id="robot-001",
        ros_stamp_millis=1780963200000,
        position_x=1.25,
        position_y=-0.4,
        position_z=0.0,
        yaw_radians=1.57,
        linear_velocity=0.22,
        angular_velocity=0.05,
    )

    payload = odometry_to_telemetry(sample, FaultSimulator(seed=1, fault_probability=0.0), 1780963200123)

    assert payload["schemaVersion"] == "rms.telemetry.v2"
    assert payload["source"] == "ros2-gazebo"
    assert payload["robotId"] == "robot-001"
    assert payload["timestamp"] == 1780963200000
    assert payload["bridgeSentAtMillis"] == 1780963200123
    assert payload["positionX"] == 1.25
    assert payload["linearVelocity"] == 0.22
    assert payload["temperatureCelsius"] > 0


def test_quaternion_to_yaw_for_identity_orientation():
    assert quaternion_to_yaw(0.0, 0.0, 0.0, 1.0) == 0.0


def test_parse_robot_mappings_supports_five_namespaced_robots():
    mappings = parse_robot_mappings(
        "robot-001:/model/robot_001/odometry,robot-002:/model/robot_002/odometry,"
        "robot-003:/model/robot_003/odometry,robot-004:/model/robot_004/odometry,"
        "robot-005:/model/robot_005/odometry"
    )

    assert [mapping.robot_id for mapping in mappings] == [
        "robot-001",
        "robot-002",
        "robot-003",
        "robot-004",
        "robot-005",
    ]
    assert mappings[4].odom_topic == "/model/robot_005/odometry"
