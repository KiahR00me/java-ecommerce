param(
    [string]$FrontendBaseUrl = "http://localhost:5173",
    [PSCredential]$AdminCredential
)

$ErrorActionPreference = "Stop"

if ($null -eq $AdminCredential) {
    $resolvedUsername = $env:APP_SECURITY_ADMIN_USERNAME
    if ([string]::IsNullOrWhiteSpace($resolvedUsername)) {
        throw "Admin username is required. Pass -AdminCredential or set APP_SECURITY_ADMIN_USERNAME."
    }

    $resolvedSecret = $env:APP_SECURITY_ADMIN_PASSWORD
    if ([string]::IsNullOrWhiteSpace($resolvedSecret)) {
        throw "Admin password is required. Pass -AdminCredential or set APP_SECURITY_ADMIN_PASSWORD."
    }

    $secureSecret = ConvertTo-SecureString $resolvedSecret -AsPlainText -Force
    $AdminCredential = New-Object System.Management.Automation.PSCredential ($resolvedUsername, $secureSecret)
}

$trimmedBaseUrl = $FrontendBaseUrl.TrimEnd('/')

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body,
        [hashtable]$Headers,
        [int[]]$ExpectedStatusCodes
    )

    $requestHeaders = @{
        Accept = "application/json"
    }

    if ($Headers) {
        foreach ($entry in $Headers.GetEnumerator()) {
            $requestHeaders[$entry.Key] = $entry.Value
        }
    }

    $jsonBody = $null
    if ($null -ne $Body) {
        $jsonBody = $Body | ConvertTo-Json -Depth 10
        if (-not $requestHeaders.ContainsKey("Content-Type")) {
            $requestHeaders["Content-Type"] = "application/json"
        }
    }

    $invokeParams = @{
        Uri     = $Uri
        Method  = $Method
        Headers = $requestHeaders
    }

    if ($null -ne $jsonBody) {
        $invokeParams["Body"] = $jsonBody
    }

    if ($PSVersionTable.PSEdition -eq "Desktop") {
        $invokeParams["UseBasicParsing"] = $true
    }

    $statusCode = 0
    $bodyText = ""

    try {
        $response = Invoke-WebRequest @invokeParams
        $statusCode = [int]$response.StatusCode
        $bodyText = $response.Content
    }
    catch {
        if (-not $_.Exception.Response) {
            throw
        }

        $statusCode = [int]$_.Exception.Response.StatusCode
        try {
            $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
            $bodyText = $reader.ReadToEnd()
        }
        catch {
            $bodyText = ""
        }

        if ($ExpectedStatusCodes -notcontains $statusCode) {
            throw "Request failed: $Method $Uri returned HTTP $statusCode`n$bodyText"
        }
    }

    if ($ExpectedStatusCodes -notcontains $statusCode) {
        throw "Unexpected status for $Method $Uri. Expected one of: $($ExpectedStatusCodes -join ', '), actual: $statusCode`n$bodyText"
    }

    $json = $null
    if (-not [string]::IsNullOrWhiteSpace($bodyText)) {
        try {
            $json = $bodyText | ConvertFrom-Json
        }
        catch {
            $json = $null
        }
    }

    return [PSCustomObject]@{
        StatusCode = $statusCode
        BodyText   = $bodyText
        Json       = $json
    }
}

$createdProductId = $null
$createdProductName = $null
$authHeader = $null

try {
    Write-Host "[1/7] Logging in via frontend proxy..."
    $loginResponse = Invoke-ApiRequest -Method "POST" -Uri "$trimmedBaseUrl/api/auth/login" -Body @{
        username = $AdminCredential.UserName
        password = $AdminCredential.GetNetworkCredential().Password
    } -Headers @{} -ExpectedStatusCodes @(200)

    if ($null -eq $loginResponse.Json -or [string]::IsNullOrWhiteSpace([string]$loginResponse.Json.accessToken)) {
        throw "Login succeeded but accessToken was missing from response payload."
    }

    $tokenType = [string]$loginResponse.Json.tokenType
    if ([string]::IsNullOrWhiteSpace($tokenType)) {
        $tokenType = "Bearer"
    }

    $authHeader = @{ Authorization = "$tokenType $($loginResponse.Json.accessToken)" }

    Write-Host "[2/7] Fetching categories..."
    $categoriesResponse = Invoke-ApiRequest -Method "GET" -Uri "$trimmedBaseUrl/api/categories" -Body $null -Headers @{} -ExpectedStatusCodes @(200)
    if ($null -eq $categoriesResponse.Json -or $categoriesResponse.Json.Count -eq 0) {
        throw "No categories were returned by GET /api/categories."
    }

    $categoryId = [int]$categoriesResponse.Json[0].id

    Write-Host "[3/7] Listing products..."
    $listResponse = Invoke-ApiRequest -Method "GET" -Uri "$trimmedBaseUrl/api/products/cursor?limit=8&sortBy=NEWEST&sortDirection=DESC" -Body $null -Headers @{} -ExpectedStatusCodes @(200)
    $listedItems = if ($null -eq $listResponse.Json) { 0 } else { @($listResponse.Json.items).Count }

    $nonce = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $createdProductName = "Smoke Product $nonce"

    Write-Host "[4/7] Creating product..."
    $createResponse = Invoke-ApiRequest -Method "POST" -Uri "$trimmedBaseUrl/api/products" -Body @{
        name          = $createdProductName
        description   = "Created by smoke script"
        imageUrl      = $null
        price         = 29.99
        stockQuantity = 25
        categoryId    = $categoryId
        active        = $true
    } -Headers $authHeader -ExpectedStatusCodes @(201)

    if ($null -eq $createResponse.Json -or $null -eq $createResponse.Json.id) {
        throw "Create succeeded but response payload did not include product id."
    }

    $createdProductId = [int64]$createResponse.Json.id

    Write-Host "[5/7] Updating product..."
    $updatedProductName = "$createdProductName (updated)"
    $updateResponse = Invoke-ApiRequest -Method "PUT" -Uri "$trimmedBaseUrl/api/products/$createdProductId" -Body @{
        name          = $updatedProductName
        description   = "Updated by smoke script"
        imageUrl      = $null
        price         = 34.49
        stockQuantity = 18
        categoryId    = $categoryId
        active        = $true
    } -Headers $authHeader -ExpectedStatusCodes @(200)

    if ($null -eq $updateResponse.Json -or [string]$updateResponse.Json.name -ne $updatedProductName) {
        throw "Update response did not contain the expected product name."
    }

    Write-Host "[6/7] Deleting product..."
    Invoke-ApiRequest -Method "DELETE" -Uri "$trimmedBaseUrl/api/products/$createdProductId" -Body $null -Headers $authHeader -ExpectedStatusCodes @(204) | Out-Null

    Write-Host "[7/7] Verifying product deletion..."
    Invoke-ApiRequest -Method "GET" -Uri "$trimmedBaseUrl/api/products/$createdProductId" -Body $null -Headers @{} -ExpectedStatusCodes @(404) | Out-Null

    Write-Host ""
    Write-Host "Smoke flow completed successfully"
    Write-Host "Frontend proxy URL : $trimmedBaseUrl"
    Write-Host "Admin username     : $($AdminCredential.UserName)"
    Write-Host "Category used      : $categoryId"
    Write-Host "Initial list count : $listedItems"
    Write-Host "Created product id : $createdProductId"
}
finally {
    if ($null -ne $createdProductId -and $null -ne $authHeader) {
        try {
            Invoke-ApiRequest -Method "DELETE" -Uri "$trimmedBaseUrl/api/products/$createdProductId" -Body $null -Headers $authHeader -ExpectedStatusCodes @(204, 404) | Out-Null
        }
        catch {
            Write-Warning "Cleanup attempt for product id $createdProductId failed: $($_.Exception.Message)"
        }
    }
}
