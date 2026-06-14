param(
    [switch]$IncludeAlerts,
    [switch]$ResetKafkaTopic,
    [string]$KafkaTopic = "robot-telemetry"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "Resetting RMS demo data to ROS2-only sources..."
Write-Host "This removes seed/unknown robot profiles, clears Redis robot:states, and optionally resets alerts/Kafka."

docker compose up -d

Write-Host ""
Write-Host "Clearing Redis state stream..."
docker exec robot-redis redis-cli DEL robot:states | Out-Null
Write-Host "[PASS] Cleared Redis stream robot:states."

if ($IncludeAlerts) {
    docker exec robot-redis redis-cli DEL robot:alerts | Out-Null
    Write-Host "[PASS] Cleared Redis stream robot:alerts."
} else {
    Write-Host "[SKIP] Kept Redis stream robot:alerts. Pass -IncludeAlerts to clear alert stream too."
}

Write-Host ""
Write-Host "Removing PostgreSQL seed/unknown robot profiles..."
if ($IncludeAlerts) {
    docker exec robot-postgres psql -U robot -d robot_management -c "DELETE FROM robot_alerts;" | Out-Null
    Write-Host "[PASS] Cleared PostgreSQL table robot_alerts."
} else {
    docker exec robot-postgres psql -U robot -d robot_management -c "DELETE FROM robot_alerts WHERE robot_id IN (SELECT robot_id FROM robots WHERE telemetry_source IN ('seed','unknown'));" | Out-Null
}
docker exec robot-postgres psql -U robot -d robot_management -c "DELETE FROM robots WHERE telemetry_source IN ('seed','unknown');" | Out-Null
docker exec robot-postgres psql -U robot -d robot_management -c "SELECT telemetry_source, COUNT(*) FROM robots GROUP BY telemetry_source ORDER BY telemetry_source;"

if ($ResetKafkaTopic) {
    Write-Host ""
    Write-Host "Resetting Kafka topic $KafkaTopic..."
    docker exec robot-kafka kafka-topics --bootstrap-server localhost:9092 --delete --if-exists --topic $KafkaTopic | Out-Null
    Start-Sleep -Seconds 3
    docker exec robot-kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic $KafkaTopic --partitions 6 --replication-factor 1 | Out-Null
    docker exec robot-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic $KafkaTopic
} else {
    Write-Host "[SKIP] Kept Kafka topic $KafkaTopic. Pass -ResetKafkaTopic to remove old Kafka records."
}

Write-Host ""
Write-Host "[PASS] ROS2-only data reset complete."
