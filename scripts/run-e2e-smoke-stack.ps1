param(
    [string]$WorkspaceRoot = "",
    [string]$BackendDir = "",
    [string]$FrontendDir = "",
    [int]$BackendPort = 8081,
    [int]$FrontendPort = 5173,
    [string]$BackendProfile = "dev-fast",
    [int]$StartupTimeoutSeconds = 180,
    [switch]$SkipFrontendInstall,
    [PSCredential]$AdminCredential
)

$ErrorActionPreference = "Stop"
$onWindows = $env:OS -eq "Windows_NT"

if ([string]::IsNullOrWhiteSpace($BackendDir)) {
    if (-not [string]::IsNullOrWhiteSpace($WorkspaceRoot)) {
        $BackendDir = Join-Path $WorkspaceRoot "java-ecommerce"
    }
    else {
        $BackendDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    }
}

if ([string]::IsNullOrWhiteSpace($FrontendDir)) {
    $backendParentDir = (Resolve-Path (Join-Path $BackendDir "..")).Path
    $candidateSibling = Join-Path $backendParentDir "spring-api-ecommerce"
    $candidateNested = Join-Path $BackendDir "spring-api-ecommerce"

    if (Test-Path $candidateSibling) {
        $FrontendDir = $candidateSibling
    }
    elseif (Test-Path $candidateNested) {
        $FrontendDir = $candidateNested
    }
    elseif (-not [string]::IsNullOrWhiteSpace($WorkspaceRoot) -and (Test-Path (Join-Path $WorkspaceRoot "spring-api-ecommerce"))) {
        $FrontendDir = Join-Path $WorkspaceRoot "spring-api-ecommerce"
    }
}

$backendDir = $BackendDir
$frontendDir = $FrontendDir
$smokeScriptPath = Join-Path $BackendDir "scripts\smoke-react-spring-flow.ps1"

if (-not (Test-Path $backendDir)) {
    throw "Backend directory not found: $backendDir"
}
if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}
if (-not (Test-Path $smokeScriptPath)) {
    throw "Smoke script not found: $smokeScriptPath"
}

$gradleWrapperPath = if ($onWindows) {
    Join-Path $backendDir "gradlew.bat"
}
else {
    Join-Path $backendDir "gradlew"
}

$npmCommand = if ($onWindows) { "npm.cmd" } else { "npm" }

if (-not (Test-Path $gradleWrapperPath)) {
    throw "Gradle wrapper not found: $gradleWrapperPath"
}

if (-not (Get-Command $npmCommand -ErrorAction SilentlyContinue)) {
    throw "npm command not found: $npmCommand"
}

if (-not $onWindows) {
    & chmod +x $gradleWrapperPath
}

$backendBaseUrl = "http://localhost:$BackendPort"
$frontendBaseUrl = "http://localhost:$FrontendPort"

$backendOutLog = Join-Path $backendDir "build\smoke-backend.out.log"
$backendErrLog = Join-Path $backendDir "build\smoke-backend.err.log"
$frontendOutLog = Join-Path $frontendDir "smoke-frontend.out.log"
$frontendErrLog = Join-Path $frontendDir "smoke-frontend.err.log"

$backendProcess = $null
$frontendProcess = $null

$managedEnvNames = @(
    "APP_SECURITY_ADMIN_USERNAME",
    "APP_SECURITY_ADMIN_PASSWORD",
    "APP_SECURITY_CUSTOMER_USERNAME",
    "APP_SECURITY_CUSTOMER_PASSWORD",
    "APP_SECURITY_JWT_SECRET",
    "APP_SECURITY_JWT_TTL_SECONDS",
    "VITE_BACKEND_URL"
)

$previousEnv = @{}
foreach ($name in $managedEnvNames) {
    $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

function Set-ProcessEnv {
    param(
        [string]$Name,
        [string]$Value
    )

    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
}

function Restore-ProcessEnv {
    param(
        [hashtable]$SavedValues,
        [string[]]$Names
    )

    foreach ($name in $Names) {
        [Environment]::SetEnvironmentVariable($name, $SavedValues[$name], "Process")
    }
}

function Wait-HttpReady {
    param(
        [string]$Name,
        [string]$Uri,
        [int]$TimeoutSeconds,
        [int[]]$ExpectedStatusCodes = @(200)
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $invokeParams = @{
                Uri        = $Uri
                Method     = "GET"
                TimeoutSec = 5
            }

            if ($PSVersionTable.PSEdition -eq "Desktop") {
                $invokeParams["UseBasicParsing"] = $true
            }

            $response = Invoke-WebRequest @invokeParams
            if ($ExpectedStatusCodes -contains [int]$response.StatusCode) {
                Write-Host "$Name is ready at $Uri"
                return
            }
        }
        catch {
            # Keep polling until timeout.
        }

        Start-Sleep -Seconds 1
    }

    throw "$Name did not become ready at $Uri within $TimeoutSeconds seconds."
}

function Stop-ProcessTree {
    param(
        [System.Diagnostics.Process]$Process,
        [string]$Name
    )

    if ($null -eq $Process) {
        return
    }

    try {
        if ($Process.HasExited) {
            return
        }

        if ($onWindows) {
            & taskkill /PID $Process.Id /T /F *> $null
        }
        else {
            Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        }

        Write-Host "Stopped $Name process (pid $($Process.Id))."
    }
    catch {
        Write-Warning "Failed to stop $Name process: $($_.Exception.Message)"
    }
}

function Show-LogTail {
    param(
        [string]$Title,
        [string]$Path,
        [int]$TailLines = 80
    )

    if (-not (Test-Path $Path)) {
        return
    }

    Write-Host ""
    Write-Host "----- $Title (last $TailLines lines) -----"
    Get-Content -Path $Path -Tail $TailLines
}

try {
    New-Item -ItemType Directory -Path (Join-Path $backendDir "build") -Force | Out-Null

    Remove-Item $backendOutLog, $backendErrLog, $frontendOutLog, $frontendErrLog -ErrorAction SilentlyContinue

    if ($null -ne $AdminCredential) {
        Set-ProcessEnv -Name "APP_SECURITY_ADMIN_USERNAME" -Value $AdminCredential.UserName
        Set-ProcessEnv -Name "APP_SECURITY_ADMIN_PASSWORD" -Value $AdminCredential.GetNetworkCredential().Password
    }

    if ([string]::IsNullOrWhiteSpace($env:APP_SECURITY_ADMIN_USERNAME)) {
        Set-ProcessEnv -Name "APP_SECURITY_ADMIN_USERNAME" -Value "admin"
    }
    if ([string]::IsNullOrWhiteSpace($env:APP_SECURITY_ADMIN_PASSWORD)) {
        Set-ProcessEnv -Name "APP_SECURITY_ADMIN_PASSWORD" -Value "change-me-admin"
    }
    if ([string]::IsNullOrWhiteSpace($env:APP_SECURITY_CUSTOMER_USERNAME)) {
        Set-ProcessEnv -Name "APP_SECURITY_CUSTOMER_USERNAME" -Value "customer@example.com"
    }
    if ([string]::IsNullOrWhiteSpace($env:APP_SECURITY_CUSTOMER_PASSWORD)) {
        Set-ProcessEnv -Name "APP_SECURITY_CUSTOMER_PASSWORD" -Value "change-me-customer"
    }
    if ([string]::IsNullOrWhiteSpace($env:APP_SECURITY_JWT_SECRET)) {
        Set-ProcessEnv -Name "APP_SECURITY_JWT_SECRET" -Value "replace-with-a-strong-32-char-minimum-secret"
    }
    if ([string]::IsNullOrWhiteSpace($env:APP_SECURITY_JWT_TTL_SECONDS)) {
        Set-ProcessEnv -Name "APP_SECURITY_JWT_TTL_SECONDS" -Value "900"
    }

    Set-ProcessEnv -Name "VITE_BACKEND_URL" -Value $backendBaseUrl

    if (-not $SkipFrontendInstall -and -not (Test-Path (Join-Path $frontendDir "node_modules"))) {
        Write-Host "Installing frontend dependencies with npm ci..."
        & $npmCommand --prefix $frontendDir ci
        if ($LASTEXITCODE -ne 0) {
            throw "npm ci failed in $frontendDir"
        }
    }

    if ($null -eq $AdminCredential) {
        $secureSecret = ConvertTo-SecureString $env:APP_SECURITY_ADMIN_PASSWORD -AsPlainText -Force
        $AdminCredential = New-Object System.Management.Automation.PSCredential ($env:APP_SECURITY_ADMIN_USERNAME, $secureSecret)
    }

    Write-Host "Starting backend on $backendBaseUrl with profile '$BackendProfile'..."
    $springRunArgs = "--server.port=$BackendPort --spring.profiles.active=$BackendProfile"
    $backendArgs = @(
        "-p", $backendDir,
        "bootRun",
        "--args=`"$springRunArgs`""
    )
    $backendProcess = Start-Process -FilePath $gradleWrapperPath -ArgumentList $backendArgs -WorkingDirectory $backendDir -PassThru -RedirectStandardOutput $backendOutLog -RedirectStandardError $backendErrLog

    Wait-HttpReady -Name "Backend" -Uri "$backendBaseUrl/actuator/health" -TimeoutSeconds $StartupTimeoutSeconds -ExpectedStatusCodes @(200)

    Write-Host "Starting frontend on $frontendBaseUrl..."
    $frontendArgs = @(
        "--prefix", $frontendDir,
        "run", "dev", "--",
        "--host", "127.0.0.1",
        "--port", "$FrontendPort",
        "--strictPort"
    )
    $frontendProcess = Start-Process -FilePath $npmCommand -ArgumentList $frontendArgs -WorkingDirectory $frontendDir -PassThru -RedirectStandardOutput $frontendOutLog -RedirectStandardError $frontendErrLog

    Wait-HttpReady -Name "Frontend" -Uri "$frontendBaseUrl" -TimeoutSeconds $StartupTimeoutSeconds -ExpectedStatusCodes @(200)

    Write-Host "Running end-to-end smoke flow through frontend proxy..."
    & $smokeScriptPath -FrontendBaseUrl $frontendBaseUrl -AdminCredential $AdminCredential

    Write-Host ""
    Write-Host "Integrated smoke flow completed successfully."
}
catch {
    Write-Error "Integrated smoke flow failed: $($_.Exception.Message)"
    Show-LogTail -Title "Backend stdout" -Path $backendOutLog
    Show-LogTail -Title "Backend stderr" -Path $backendErrLog
    Show-LogTail -Title "Frontend stdout" -Path $frontendOutLog
    Show-LogTail -Title "Frontend stderr" -Path $frontendErrLog
    throw
}
finally {
    Stop-ProcessTree -Process $frontendProcess -Name "frontend"
    Stop-ProcessTree -Process $backendProcess -Name "backend"
    Restore-ProcessEnv -SavedValues $previousEnv -Names $managedEnvNames
}
