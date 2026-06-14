param(
    [string]$NodePath = "D:\AI_Lab\node.exe",
    [int]$Port = 5173
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot "frontend"
$vitePath = Join-Path $frontendDir "node_modules\vite\bin\vite.js"

if (-not (Test-Path -LiteralPath $NodePath)) {
    Write-Error "Node.js was not found at $NodePath. Update -NodePath or install Node.js."
}

if (-not (Test-Path -LiteralPath $vitePath)) {
    Write-Error "Vite was not found at $vitePath. Run npm.cmd install in frontend first."
}

Write-Host "Starting frontend dev server on http://localhost:$Port"
Write-Host "Keep this PowerShell window open while using the frontend. Press Ctrl+C to stop."
Write-Host ""

Set-Location $frontendDir
& $NodePath ".\node_modules\vite\bin\vite.js" "--host" "0.0.0.0" "--port" "$Port"
