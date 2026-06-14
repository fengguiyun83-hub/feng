from glob import glob
from setuptools import setup

package_name = "rms_warehouse_sim"

setup(
    name=package_name,
    version="0.1.0",
    packages=[package_name],
    data_files=[
        ("share/ament_index/resource_index/packages", [f"resource/{package_name}"]),
        (f"share/{package_name}", ["package.xml"]),
        (f"share/{package_name}/launch", glob("launch/*.launch.py")),
        (f"share/{package_name}/worlds", glob("worlds/*.world")),
        (f"share/{package_name}/models/rms_agv", glob("models/rms_agv/*")),
    ],
    install_requires=["setuptools"],
    zip_safe=True,
    maintainer="Robot RMS",
    maintainer_email="robot@example.com",
    description="Smart warehouse Gazebo simulation and patrol nodes for RMS.",
    license="MIT",
    entry_points={
        "console_scripts": [
            "rms_patrol_node = rms_warehouse_sim.patrol_node:main",
            "patrol_node.py = rms_warehouse_sim.patrol_node:main",
        ],
    },
)
