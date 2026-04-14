param(
    [string]$BaseUrl = "http://localhost:8080",
    [PSCredential]$AdminCredential,
    [string]$Email = "",
    [string]$FullName = "Demo Verified Customer"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = "demo-" + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + "@example.com"
}

if ($null -eq $AdminCredential) {
    $defaultAdminUsername = $env:APP_SECURITY_ADMIN_USERNAME
    $defaultAdminPassword = $env:APP_SECURITY_ADMIN_PASSWORD

    if ([string]::IsNullOrWhiteSpace($defaultAdminUsername) -or [string]::IsNullOrWhiteSpace($defaultAdminPassword)) {
        throw "Admin credentials are required. Pass -AdminCredential or set APP_SECURITY_ADMIN_USERNAME and APP_SECURITY_ADMIN_PASSWORD."
    }

    $secure = ConvertTo-SecureString $defaultAdminPassword -AsPlainText -Force
    $AdminCredential = New-Object System.Management.Automation.PSCredential ($defaultAdminUsername, $secure)
}

function Invoke-JsonApi {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body,
        [string]$Authorization,
        [int[]]$ExpectedStatusCodes
    )

    $headers = @{
        "Accept" = "application/json"
    }

    if (-not [string]::IsNullOrWhiteSpace($Authorization)) {
        $headers["Authorization"] = $Authorization
    }

    $json = $null
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10
        $headers["Content-Type"] = "application/json"
    }

    try {
        $invokeParams = @{
            Uri     = $Uri
            Method  = $Method
            Headers = $headers
        }

        if ($null -ne $json) {
            $invokeParams["Body"] = $json
        }

        if ($PSVersionTable.PSEdition -eq "Desktop") {
            $invokeParams["UseBasicParsing"] = $true
        }

        $response = Invoke-WebRequest @invokeParams
    }
    catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            $bodyText = ""
            try {
                $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
                $bodyText = $reader.ReadToEnd()
            }
            catch {
                $bodyText = "<no response body>"
            }
            throw "Request failed: $Method $Uri returned HTTP $statusCode`n$bodyText"
        }
        throw
    }

    $actual = [int]$response.StatusCode
    if ($ExpectedStatusCodes -notcontains $actual) {
        throw "Unexpected status code for $Method $Uri. Expected one of: $($ExpectedStatusCodes -join ', '), actual: $actual"
    }

    if ([string]::IsNullOrWhiteSpace($response.Content)) {
        return $null
    }

    return $response.Content | ConvertFrom-Json
}

Write-Host "[0/4] Logging in as admin..."
$adminAccessTokenResponse = Invoke-JsonApi -Method "POST" -Uri "$BaseUrl/api/auth/login" -Body @{
    username = $AdminCredential.UserName
    password = $AdminCredential.GetNetworkCredential().Password
} -Authorization $null -ExpectedStatusCodes @(200)

$tokenType = [string]$adminAccessTokenResponse.tokenType
if ([string]::IsNullOrWhiteSpace($tokenType)) {
    $tokenType = "Bearer"
}

$accessToken = [string]$adminAccessTokenResponse.accessToken
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw "Login did not return an access token."
}

$adminAuth = "$tokenType $accessToken"

Write-Host "[1/4] Creating customer as admin..."
$created = Invoke-JsonApi -Method "POST" -Uri "$BaseUrl/api/customers" -Body @{
    email    = $Email
    fullName = $FullName
} -Authorization $adminAuth -ExpectedStatusCodes @(201)
$customerId = [int64]$created.id
Write-Host "Created customer ID: $customerId"

Write-Host "[2/4] Sending verification email..."
Invoke-JsonApi -Method "POST" -Uri "$BaseUrl/api/customers/$customerId/send-verification" -Body $null -Authorization $adminAuth -ExpectedStatusCodes @(202) | Out-Null
Write-Host "Verification dispatch request accepted."

Write-Host "[3/4] Fetching token from dev mail sandbox endpoint..."
$emailEscaped = [Uri]::EscapeDataString($Email)
$tokenResult = Invoke-JsonApi -Method "GET" -Uri "$BaseUrl/api/dev/mail-sandbox/customers/verification-token?email=$emailEscaped" -Body $null -Authorization $adminAuth -ExpectedStatusCodes @(200)
$token = [string]$tokenResult.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Sandbox endpoint returned an empty token."
}
Write-Host "Token retrieved for $Email"

Write-Host "[4/4] Verifying customer by token..."
Invoke-JsonApi -Method "POST" -Uri "$BaseUrl/api/customers/verify" -Body @{ token = $token } -Authorization $null -ExpectedStatusCodes @(204) | Out-Null
Write-Host "Verification completed."

Write-Host ""
Write-Host "Demo flow completed successfully"
Write-Host "Email: $Email"
Write-Host "Customer ID: $customerId"
