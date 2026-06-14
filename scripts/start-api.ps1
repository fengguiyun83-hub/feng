param(
    [string]$MavenPath = "D:\Tools\apache-maven-3.9.9\bin\mvn.cmd"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$localRepo = Join-Path $projectRoot ".m2\repository"
$backendDir = Join-Path $projectRoot "rms-backend-api"

if (-not (Test-Path -LiteralPath $MavenPath)) {
    Write-Error "Maven was not found at $MavenPath. Update the MavenPath parameter or install Maven."
}

if (-not (Test-Path -LiteralPath $backendDir)) {
    Write-Error "Backend module was not found at $backendDir."
}

Write-Host "Starting Robot Management API on http://localhost:8080"
Write-Host "Project: $projectRoot"
Write-Host "Backend: $backendDir"
Write-Host "Maven:   $MavenPath"
Write-Host "Repo:    $localRepo"
Write-Host ""
Write-Host "Keep this terminal open while using the API. Press Ctrl+C to stop."
Write-Host ""

Write-Host "Installing common module into the project-local Maven repository..."
Set-Location $projectRoot
& $MavenPath "-Dmaven.repo.local=$localRepo" "-pl" "rms-common" "-am" "-DskipTests" install

Write-Host ""
Write-Host "Launching Spring Boot API from rms-backend-api..."
Set-Location $backendDir
& $MavenPath "-Dmaven.repo.local=$localRepo" spring-boot:run
