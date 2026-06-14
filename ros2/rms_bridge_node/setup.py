from setuptools import setup

package_name = "rms_bridge_node"

setup(
    name=package_name,
    version="0.1.0",
    packages=[package_name],
    data_files=[
        ("share/ament_index/resource_index/packages", [f"resource/{package_name}"]),
        (f"share/{package_name}", ["package.xml"]),
    ],
    install_requires=["setuptools", "kafka-python"],
    zip_safe=True,
    maintainer="Robot RMS",
    maintainer_email="robot@example.com",
    description="ROS 2 odometry to Kafka telemetry bridge for RMS.",
    license="MIT",
    entry_points={
        "console_scripts": [
            "rms_bridge_node = rms_bridge_node.bridge_node:main",
        ],
    },
)
