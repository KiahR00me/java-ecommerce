param(
    [int]$Port = 8081,
    [string]$Profile = "dev-fast",
    [switch]$NoDaemon
)

$ErrorActionPreference = "Stop"
$onWindows = $env:OS -eq "Windows_NT"

$backendDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$gradleWrapper = if ($onWindows) {
    Join-Path $backendDir "gradlew.bat"
}
else {
    Join-Path $backendDir "gradlew"
}

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}

if (-not $onWindows) {
    & chmod +x $gradleWrapper
}

$springArgs = "--server.port=$Port --spring.profiles.active=$Profile"
$gradleArgs = @("bootRun", "--args=`"$springArgs`"")
if ($NoDaemon) {
    $gradleArgs += "--no-daemon"
}

Push-Location $backendDir
try {
    Write-Host "Starting backend on http://localhost:$Port with profile '$Profile'..."
    & $gradleWrapper @gradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}