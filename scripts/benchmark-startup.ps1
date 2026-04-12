param(
    [string]$GradleCmd = ".\\gradlew.bat"
)

$ErrorActionPreference = "Continue"

function Wait-PostgresReady {
    param(
        [int]$MaxAttempts = 30,
        [int]$DelaySeconds = 2
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        docker compose exec -T postgres pg_isready -U ecommerce -d ecommerce *> $null
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Start-Sleep -Seconds $DelaySeconds
    }

    throw "PostgreSQL did not become ready in time."
}

function Invoke-StartupBenchmark {
    param(
        [string]$ProfileName,
        [string]$AppArgs
    )

    Write-Host "Running benchmark for profile: $ProfileName"
    $gradleArgs = @("bootRun", "--no-daemon", "--args=$AppArgs")
    $output = & $GradleCmd @gradleArgs 2>&1 | Out-String

    $regex = [regex]"Started EcommerceApplication in\s+([0-9.]+)\s+seconds"
    $match = $regex.Match($output)

    if (-not $match.Success) {
        if ($output -match "Connection to localhost:5432 refused") {
            throw "Portfolio benchmark failed because PostgreSQL is not reachable on localhost:5432."
        }
        throw "Could not parse startup time for profile $ProfileName. Ensure the app starts successfully."
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
    Wait-PostgresReady
    $results += Invoke-StartupBenchmark -ProfileName "portfolio" -AppArgs "--spring.profiles.active=portfolio $commonArgs"
}
else {
    Write-Host "Skipping portfolio benchmark because Docker is not available."
}

Write-Host ""
Write-Host "Startup Benchmark Summary"
$results | Sort-Object StartupSeconds | Format-Table -AutoSize
