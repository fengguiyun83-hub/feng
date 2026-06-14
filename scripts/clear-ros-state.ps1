param(
    [switch]$IncludeAlerts
)

$ErrorActionPreference = "Stop"

Write-Host "Clearing Redis streams for ROS-only telemetry verification..."
Write-Host "This does not delete PostgreSQL robot profiles or Kafka topics."

docker exec robot-redis redis-cli DEL robot:states | Out-Null
Write-Host "[PASS] Cleared Redis stream robot:states."

if ($IncludeAlerts) {
    docker exec robot-redis redis-cli DEL robot:alerts | Out-Null
    Write-Host "[PASS] Cleared Redis stream robot:alerts."
} else {
    Write-Host "[SKIP] robot:alerts was kept. Pass -IncludeAlerts to clear alert history in Redis too."
}

Write-Host ""
Write-Host "Next recommended steps:"
Write-Host "1. Stop old Flink jobs from http://localhost:8081 or with docker exec robot-flink-jobmanager flink cancel <jobId>."
Write-Host "2. Submit the ROS-only Flink job with --group.id robot-ros2-only-flink-v1 --telemetry.required-source ros2-gazebo."
Write-Host "3. Start the ROS 2 warehouse world, bridge node, and patrol node."
Write-Host "4. Run scripts/verify-stream.ps1 and scripts/verify-api.ps1."
