from launch import LaunchDescription
from launch.actions import IncludeLaunchDescription
from launch.launch_description_sources import PythonLaunchDescriptionSource
from launch.substitutions import PathJoinSubstitution
from launch_ros.actions import Node
from launch_ros.substitutions import FindPackageShare


ROBOTS = [
    ("robot-001", -5.5, -3.2, 0.0),
    ("robot-002", -2.5, -1.3, 0.3),
    ("robot-003", 0.0, 1.3, -0.2),
    ("robot-004", 2.7, -1.4, 0.6),
    ("robot-005", 5.4, 3.2, 3.14),
]


def ros_name(robot_id: str) -> str:
    return robot_id.replace("-", "_")


def gz_topic(robot_id: str, topic: str) -> str:
    return f"/model/{ros_name(robot_id)}/{topic}"


def bridge_argument(robot_id: str, topic: str, ros_type: str, gz_type: str) -> str:
    return f"{gz_topic(robot_id, topic)}@{ros_type}@{gz_type}"


def generate_launch_description():
    package_share = FindPackageShare("rms_warehouse_sim")
    world_path = PathJoinSubstitution([package_share, "worlds", "rms_smart_warehouse.world"])
    model_path = PathJoinSubstitution([package_share, "models", "rms_agv", "model.sdf"])
    gz_launch_path = PathJoinSubstitution([
        FindPackageShare("ros_gz_sim"),
        "launch",
        "gz_sim.launch.py",
    ])

    nodes = [
        IncludeLaunchDescription(
            PythonLaunchDescriptionSource([gz_launch_path]),
            launch_arguments={"gz_args": ["-r ", world_path]}.items(),
        )
    ]

    for robot_id, x, y, yaw in ROBOTS:
        model_name = ros_name(robot_id)
        nodes.append(
            Node(
                package="ros_gz_sim",
                executable="create",
                output="screen",
                arguments=[
                    "-name",
                    model_name,
                    "-file",
                    model_path,
                    "-x",
                    str(x),
                    "-y",
                    str(y),
                    "-z",
                    "0.12",
                    "-Y",
                    str(yaw),
                ],
            )
        )
        nodes.append(
            Node(
                package="ros_gz_bridge",
                executable="parameter_bridge",
                output="screen",
                arguments=[
                    bridge_argument(
                        robot_id,
                        "odometry",
                        "nav_msgs/msg/Odometry",
                        "gz.msgs.Odometry",
                    ),
                    bridge_argument(
                        robot_id,
                        "cmd_vel",
                        "geometry_msgs/msg/Twist",
                        "gz.msgs.Twist",
                    ),
                ],
            )
        )

    return LaunchDescription(nodes)
