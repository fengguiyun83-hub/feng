param(
    [string]$MavenPath = "D:\Tools\apache-maven-3.9.9\bin\mvn.cmd",
    [string]$Repository = ".m2/repository"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Test-Path -LiteralPath $MavenPath)) {
    Write-Error "Maven was not found at $MavenPath. Update the MavenPath parameter or install Maven."
}

Write-Host "Running API, stream, persistence, and robot state tests..."
& $MavenPath "-Dmaven.repo.local=$Repository" "-pl" "rms-backend-api" "-am" "-Dtest=RobotApiControllerTest,DashboardStreamControllerTest,RobotPersistenceTest,AlertPersistenceServiceTest,RobotStateSyncServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
