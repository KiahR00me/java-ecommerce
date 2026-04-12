param(
    [string]$GradleCmd = ".\\gradlew.bat"
)

$ErrorActionPreference = "Continue"

function Invoke-StartupBenchmark {
    param(
        [string]$ProfileName,
        [string]$AppArgs
    )

    Write-Host "Running benchmark for profile: $ProfileName"
    $gradleArgs = @("bootRun", "--no-daemon", "--args=$AppArgs")
    $output = & $GradleCmd @gradleArgs 2>&1 | Out-String

    $regex = [regex]"Started EcommerceApplication in ([0-9.]+) seconds"
    $match = $regex.Match($output)

    if (-not $match.Success) {
        throw "Could not parse startup time for profile $ProfileName."
    }

    [double]$seconds = $match.Groups[1].Value

    [PSCustomObject]@{
        Profile        = $ProfileName
        StartupSeconds = $seconds
    }
}

$commonArgs = "--spring.main.web-application-type=none --server.port=0 --spring.devtools.restart.enabled=false"

$results = @()
$results += Invoke-StartupBenchmark -ProfileName "dev-fast" -AppArgs "--spring.profiles.active=dev-fast $commonArgs"

$dockerStatus = docker info --format "{{.ServerVersion}}" 2>$null
if ($LASTEXITCODE -eq 0) {
    docker compose up -d postgres | Out-Null
    $results += Invoke-StartupBenchmark -ProfileName "portfolio" -AppArgs "--spring.profiles.active=portfolio $commonArgs"
}
else {
    Write-Host "Skipping portfolio benchmark because Docker is not available."
}

Write-Host ""
Write-Host "Startup Benchmark Summary"
$results | Sort-Object StartupSeconds | Format-Table -AutoSize
