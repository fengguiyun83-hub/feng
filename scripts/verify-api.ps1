param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$ExpectedRobotCount = 5,
    [switch]$AllowMissingRos2
)

$ErrorActionPreference = "Stop"

function Invoke-ApiJson {
    param([string]$Path)
    $uri = "$BaseUrl$Path"
    try {
        return Invoke-RestMethod -Uri $uri -TimeoutSec 5
    } catch {
        throw "Request failed: $uri. Start the API first with scripts/start-api.ps1. Details: $($_.Exception.Message)"
    }
}

Write-Host "Verifying Robot Management API at $BaseUrl"

$system = Invoke-ApiJson "/api/system/status"
$robots = Invoke-ApiJson "/api/robots"
$summary = Invoke-ApiJson "/api/dashboard/summary"

$robotCount = @($robots).Count
if ($ExpectedRobotCount -ge 0 -and $robotCount -ne $ExpectedRobotCount) {
    throw "Expected $ExpectedRobotCount robots from /api/robots, but got $robotCount."
}

if ($ExpectedRobotCount -ge 0 -and $summary.robotTotal -ne $ExpectedRobotCount) {
    throw "Expected dashboard robotTotal to be $ExpectedRobotCount, but got $($summary.robotTotal)."
}

$redis = @($system.components) | Where-Object { $_.name -eq "redis" } | Select-Object -First 1
$ros2 = @($system.components) | Where-Object { $_.name -eq "ros2_bridge" } | Select-Object -First 1
$robot001 = @($robots) | Where-Object { $_.robotId -eq "robot-001" } | Select-Object -First 1
$warehouseRobots = @("robot-001", "robot-002", "robot-003", "robot-004", "robot-005") | ForEach-Object {
    $robotId = $_
    @($robots) | Where-Object { $_.robotId -eq $robotId } | Select-Object -First 1
}

Write-Host ""
Write-Host "API verification passed."
Write-Host "System status:      $($system.status)"
Write-Host "Robot count:        $robotCount"
Write-Host "Dashboard robots:   $($summary.robotTotal)"
Write-Host "Online robots:      $($summary.onlineRobots)"
Write-Host "Warning robots:     $($summary.warningRobots)"
Write-Host "Open alerts:        $($summary.openAlertCount)"
Write-Host "Redis alert count:  $($summary.redisAlertCount)"
if ($redis) {
    Write-Host "Redis status:       $($redis.status) - $($redis.detail)"
} else {
    Write-Host "Redis status:       not reported"
}
if ($ros2) {
    Write-Host "ROS 2 bridge:       $($ros2.status) - $($ros2.detail)"
}
if ($robot001) {
    Write-Host "robot-001 pose:     x=$($robot001.positionX), y=$($robot001.positionY), yaw=$($robot001.yawRadians), source=$($robot001.telemetrySource)"
}
Write-Host "Warehouse robots:"
$warehouseRobots | ForEach-Object {
    if ($null -eq $_) {
        Write-Host "  missing robot"
    } else {
        Write-Host "  $($_.robotId): x=$($_.positionX), y=$($_.positionY), source=$($_.telemetrySource), lastSeen=$($_.lastSeenAt)"
    }
}

$invalidSources = @($warehouseRobots | Where-Object {
    $null -eq $_ -or $_.telemetrySource -ne "ros2-gazebo"
})
$zeroPoseRobots = @($warehouseRobots | Where-Object {
    $null -ne $_ -and [math]::Abs([double]$_.positionX) -lt 0.0001 -and [math]::Abs([double]$_.positionY) -lt 0.0001
})

if ($invalidSources.Count -gt 0) {
    $details = $invalidSources | ForEach-Object {
        if ($null -eq $_) {
            "missing"
        } else {
            "$($_.robotId) source=$($_.telemetrySource)"
        }
    }
    $message = "ROS-only verification found robots not sourced from ros2-gazebo: $($details -join ', ')"
    if ($AllowMissingRos2) {
        Write-Host "[WARN] $message"
    } else {
        throw $message
    }
}

if ($zeroPoseRobots.Count -gt 0) {
    $ids = $zeroPoseRobots | ForEach-Object { $_.robotId }
    Write-Host "[WARN] These ROS robots are still at x=0,y=0. If the patrol node is running, run this check again after a few seconds: $($ids -join ', ')"
}
