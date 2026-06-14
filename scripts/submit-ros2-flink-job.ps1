param(
    [string]$MavenPath = "D:\Tools\apache-maven-3.9.9\bin\mvn.cmd",
    [string]$GroupId = "robot-ros2-only-flink-v2",
    [double]$AlertThreshold = 45,
    [int]$StateEmitIntervalMs = 200
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$localRepo = Join-Path $projectRoot ".m2\repository"
$jobJar = Join-Path $projectRoot "rms-flink-job\target\rms-flink-job-0.0.1-SNAPSHOT.jar"
Set-Location $projectRoot

if (-not (Test-Path -LiteralPath $MavenPath)) {
    Write-Error "Maven was not found at $MavenPath. Update -MavenPath or install Maven."
}

Write-Host "Building Flink job..."
& $MavenPath "-Dmaven.repo.local=$localRepo" "-pl" "rms-flink-job" "-am" "-DskipTests" package

Write-Host ""
Write-Host "Ensuring Docker Compose services are running..."
docker compose up -d

Write-Host ""
Write-Host "Copying Flink job jar to JobManager..."
docker exec robot-flink-jobmanager mkdir -p /opt/flink/usrlib
docker cp $jobJar robot-flink-jobmanager:/opt/flink/usrlib/robot-job.jar

Write-Host ""
Write-Host "Cancelling existing Robot Temperature Alert and State Flink jobs..."
$listOutput = docker exec robot-flink-jobmanager flink list 2>&1
$jobIds = $listOutput | Select-String -Pattern "([a-f0-9]{32})\s+:\s+Robot Temperature Alert and State Flink Job" | ForEach-Object {
    $_.Matches[0].Groups[1].Value
}
foreach ($jobId in $jobIds) {
    docker exec robot-flink-jobmanager flink cancel $jobId | Out-Null
    Write-Host "[PASS] Cancelled Flink job $jobId"
}
if (-not $jobIds) {
    Write-Host "[SKIP] No existing matching Flink jobs found."
}

Write-Host ""
Write-Host "Submitting ROS2-only Flink job..."
docker exec robot-flink-jobmanager flink run -d /opt/flink/usrlib/robot-job.jar `
    --bootstrap.servers kafka:19092 `
    --group.id $GroupId `
    --redis.host redis `
    --alert.threshold $AlertThreshold `
    --redis.state-stream robot:states `
    --state.emit-interval-ms $StateEmitIntervalMs `
    --telemetry.required-source ros2-gazebo

Write-Host ""
docker exec robot-flink-jobmanager flink list
