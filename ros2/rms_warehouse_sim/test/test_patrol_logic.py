from rms_warehouse_sim.patrol_logic import ROBOT_IDS, cmd_vel_topic, command_for_elapsed, ros_name


def test_default_robot_ids_cover_five_rms_robots():
    assert ROBOT_IDS == ["robot-001", "robot-002", "robot-003", "robot-004", "robot-005"]


def test_ros_name_sanitizes_rms_robot_id_for_ros_topics():
    assert ros_name("robot-003") == "robot_003"


def test_cmd_vel_topic_uses_gazebo_sim_safe_model_namespace():
    assert cmd_vel_topic("robot-003") == "/model/robot_003/cmd_vel"


def test_patrol_command_changes_across_phases():
    straight = command_for_elapsed(1.0, 0)
    turning = command_for_elapsed(5.0, 0)
    pause = command_for_elapsed(11.8, 0)

    assert straight.linear_x > 0.0
    assert turning.angular_z > 0.0
    assert pause.linear_x == 0.0
