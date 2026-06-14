from dataclasses import dataclass


ROBOT_IDS = ["robot-001", "robot-002", "robot-003", "robot-004", "robot-005"]


@dataclass(frozen=True)
class VelocityCommand:
    linear_x: float
    angular_z: float


def command_for_elapsed(elapsed_seconds: float, robot_index: int) -> VelocityCommand:
    phase = (elapsed_seconds + robot_index * 1.7) % 12.0
    speed = 0.13 + robot_index * 0.012
    turn = 0.35 + robot_index * 0.025
    if phase < 4.2:
        return VelocityCommand(linear_x=speed, angular_z=0.0)
    if phase < 6.0:
        return VelocityCommand(linear_x=0.02, angular_z=turn)
    if phase < 9.8:
        return VelocityCommand(linear_x=speed * 0.85, angular_z=0.0)
    if phase < 11.4:
        return VelocityCommand(linear_x=0.02, angular_z=-turn)
    return VelocityCommand(linear_x=0.0, angular_z=0.0)


def ros_name(robot_id: str) -> str:
    return robot_id.replace("-", "_")


def cmd_vel_topic(robot_id: str) -> str:
    return f"/model/{ros_name(robot_id)}/cmd_vel"
