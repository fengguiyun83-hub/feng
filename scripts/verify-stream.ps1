$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Invoke-CommandCapture {
    param(
        [string[]]$Command
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($Command.Count -gt 1) {
            $output = & $Command[0] @($Command[1..($Command.Count - 1)]) 2>&1
        } else {
            $output = & $Command[0] 2>&1
        }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($null -eq $exitCode) {
        $exitCode = 0
    }

    return [PSCustomObject]@{
        Output = $output
        ExitCode = $exitCode
        CommandText = ($Command -join " ")
    }
}

function Invoke-OptionalCommand {
    param(
        [string]$Title,
        [string[]]$Command
    )

    Write-Host ""
    Write-Host "== $Title =="
    $result = Invoke-CommandCapture $Command
    $result.Output | ForEach-Object { Write-Host $_ }

    if ($result.ExitCode -ne 0) {
        Write-Host "[WARN] Optional check failed: $($result.CommandText)"
    }

    return $result
}

function Invoke-RequiredCommand {
    param(
        [string]$Title,
        [string[]]$Command
    )

    Write-Host ""
    Write-Host "== $Title =="
    $result = Invoke-CommandCapture $Command
    $result.Output | ForEach-Object { Write-Host $_ }

    if ($result.ExitCode -ne 0) {
        throw "Required command failed: $($result.CommandText)"
    }

    return $result.Output
}

Write-Host "Verifying Kafka/Flink/Redis stream environment"
Write-Host "Redis Stream robot:alerts is the required alert check. Redis Stream robot:states is the ROS 2 digital twin check."

Invoke-OptionalCommand "Docker Compose services" @("docker", "compose", "ps") | Out-Null

Invoke-OptionalCommand "Flink jobs" @("docker", "exec", "robot-flink-jobmanager", "flink", "list") | Out-Null

try {
    $alertCountText = Invoke-RequiredCommand "Redis XLEN robot:alerts" @("docker", "exec", "robot-redis", "redis-cli", "XLEN", "robot:alerts")
    $firstLine = ($alertCountText | Where-Object { $_ -ne $null -and "$_".Trim().Length -gt 0 } | Select-Object -First 1)

    $alertCount = 0
    if (-not [int]::TryParse("$firstLine", [ref]$alertCount)) {
        throw "Could not parse Redis alert stream length: $alertCountText"
    }

    Invoke-RequiredCommand "Redis XREVRANGE robot:alerts" @("docker", "exec", "robot-redis", "redis-cli", "XREVRANGE", "robot:alerts", "+", "-", "COUNT", "5") | Out-Null

    $stateCountText = Invoke-RequiredCommand "Redis XLEN robot:states" @("docker", "exec", "robot-redis", "redis-cli", "XLEN", "robot:states")
    $stateFirstLine = ($stateCountText | Where-Object { $_ -ne $null -and "$_".Trim().Length -gt 0 } | Select-Object -First 1)
    $stateCount = 0
    if (-not [int]::TryParse("$stateFirstLine", [ref]$stateCount)) {
        throw "Could not parse Redis state stream length: $stateCountText"
    }
    $stateSample = @()
    if ($stateCount -gt 0) {
        Invoke-RequiredCommand "Redis XREVRANGE robot:states" @("docker", "exec", "robot-redis", "redis-cli", "XREVRANGE", "robot:states", "+", "-", "COUNT", "1") | Out-Null
        $stateSample = @(Invoke-RequiredCommand "Redis recent robot:states sample" @("docker", "exec", "robot-redis", "redis-cli", "XREVRANGE", "robot:states", "+", "-", "COUNT", "20"))
    }

    Write-Host ""
    if ($alertCount -eq 0) {
        Write-Host "[WARN] Stream verification completed, but robot:alerts is empty."
        Write-Host "This usually means no alert data has been generated yet."
        Write-Host "To generate ROS-only alerts, submit the Flink job with --alert.threshold 40 --telemetry.required-source ros2-gazebo and keep the ROS 2 bridge running."
    } else {
        Write-Host "[PASS] Stream verification passed. Redis robot:alerts contains $alertCount entries."
        Write-Host "Recent alerts were shown above."
    }
    if ($stateCount -eq 0) {
        Write-Host "[WARN] Redis robot:states is empty. Start the 5-robot warehouse world, submit Flink with --redis.state-stream robot:states --state.emit-interval-ms 200 --telemetry.required-source ros2-gazebo, and run rms_bridge_node with robot_mappings."
    } else {
        Write-Host "[PASS] Redis robot:states contains $stateCount rate-limited digital twin entries."
        Write-Host "Expected robot IDs in recent samples: robot-001, robot-002, robot-003, robot-004, robot-005."
        $sampleText = $stateSample -join "`n"
        $robotIds = [regex]::Matches($sampleText, "robot-\d{3}") | ForEach-Object { $_.Value } | Sort-Object -Unique
        $rosSourceCount = ([regex]::Matches($sampleText, "ros2-gazebo")).Count
        $unknownSourceCount = ([regex]::Matches($sampleText, "\bunknown\b")).Count
        if ($robotIds.Count -gt 0) {
            Write-Host "Recent robot IDs: $($robotIds -join ', ')"
        }
        Write-Host "Recent source counts: ros2-gazebo=$rosSourceCount, unknown=$unknownSourceCount"
        if ($unknownSourceCount -gt 0) {
            Write-Host "[WARN] Recent robot:states still contains source=unknown. Run scripts/clear-ros-state.ps1, stop old Flink jobs, and resubmit with --telemetry.required-source ros2-gazebo."
        }
    }
} catch {
    Write-Host ""
    Write-Host "[FAIL] Redis Stream verification failed."
    Write-Host "Check whether robot-redis is running, the container name is correct, and robot:alerts is reachable."
    Write-Host $_
    exit 1
}
