Set-Location "c:\Users\Siddhesh Akole\OneDrive\Documents\Desktop\Hack-1-\printease"

$email = if ($env.JWT_TEST_EMAIL) { $env.JWT_TEST_EMAIL } else { 'test@example.com' }
$pass = if ($env.JWT_TEST_PASSWORD) { $env.JWT_TEST_PASSWORD } else { 'ChangeMe123!' }
$timeoutSec = 15

function Decode-Base64Url([string]$value) {
    $s = $value.Replace('-', '+').Replace('_', '/')
    switch ($s.Length % 4) {
        2 { [void]($s += '==') }
        3 { [void]($s += '=') }
    }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($s))
}

function Encode-Base64Url([byte[]]$bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=') -replace '\+', '-' -replace '/', '_'
}

function Get-FirstNonEmpty([object[]]$values) {
    foreach ($v in $values) {
        $s = [string]$v
        if (-not [string]::IsNullOrWhiteSpace($s)) {
            return $s
        }
    }
    return ''
}

$loginBody = @{ email = $email; password = $pass } | ConvertTo-Json
$login = Invoke-RestMethod -UseBasicParsing -Method POST -Uri 'http://localhost:8080/auth/login' -ContentType 'application/json' -Body $loginBody -TimeoutSec $timeoutSec
$loginData = $login.data
$tokenRaw = Get-FirstNonEmpty @($loginData.accessToken, $loginData.token, $login.accessToken, $login.token)
if ([string]::IsNullOrWhiteSpace($tokenRaw)) {
    throw 'Login response did not include a usable access token.'
}

if ($tokenRaw.StartsWith('Bearer ', [System.StringComparison]::OrdinalIgnoreCase)) {
    [string]$token = $tokenRaw.Substring(7).Trim()
} else {
    [string]$token = $tokenRaw.Trim()
}

$tokenParts = $token -split '\.'
if ($tokenParts.Count -lt 2 -or [string]::IsNullOrWhiteSpace($tokenParts[1])) {
    throw 'Access token is not a valid JWT with a payload segment.'
}

$payloadJson = Decode-Base64Url $tokenParts[1]
try {
    $payload = $payloadJson | ConvertFrom-Json -ErrorAction Stop
} catch {
    throw "Unable to parse decoded JWT payload: $($_.Exception.Message)"
}
$issuer = [string]$payload.iss
$subject = [string]$payload.sub
if ($payload.aud -is [System.Array]) {
    $audience = (($payload.aud | ForEach-Object { [string]$_ }) -join ',')
} else {
    $audience = [string]$payload.aud
}
$userId = Get-FirstNonEmpty @($loginData.userId, $loginData.id, $payload.sub)
$userEmail = Get-FirstNonEmpty @($loginData.email, $payload.email)
$userFirstName = Get-FirstNonEmpty @($loginData.firstName, $payload.firstName)
$userLastName = Get-FirstNonEmpty @($loginData.lastName, $payload.lastName)

Write-Output "LOGIN_OK: $($login.success)"
Write-Output "TOKEN_ISSUER: $issuer"
Write-Output "TOKEN_AUDIENCE: $audience"
Write-Output "TOKEN_SUB: $subject"

try {
    $validResp = Invoke-WebRequest -UseBasicParsing -Method GET -Uri 'http://localhost:8080/api/cart/count' -Headers @{ Authorization = "Bearer $token" } -TimeoutSec $timeoutSec
    $validBody = $validResp.Content
    if ($validBody -is [byte[]]) {
        $validBody = [System.Text.Encoding]::UTF8.GetString($validBody)
    }
    Write-Output "VALID_TOKEN_STATUS: $($validResp.StatusCode)"
    Write-Output "VALID_TOKEN_BODY: $validBody"
} catch {
    if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Output "VALID_TOKEN_STATUS: $status"
        Write-Output "VALID_TOKEN_BODY: $body"
    } else {
        Write-Output "VALID_TOKEN_STATUS: ERR"
    }
}

$jwtSecret = [string]$env.JWT_SECRET
if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
    throw 'JWT_SECRET is not set in environment. Set JWT_SECRET before running this script.'
}
$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

$headerObj = @{ alg = 'HS256'; typ = 'JWT' }
$payloadObj = @{
    sub = $userId
    email = $userEmail
    firstName = $userFirstName
    lastName = $userLastName
    role = 'STUDENT'
    iss = 'qprint-auth'
    aud = 'wrong-audience'
    iat = $now
    exp = ($now + 600)
}

$headerPart = Encode-Base64Url([System.Text.Encoding]::UTF8.GetBytes(($headerObj | ConvertTo-Json -Compress)))
$payloadPart = Encode-Base64Url([System.Text.Encoding]::UTF8.GetBytes(($payloadObj | ConvertTo-Json -Compress)))
$unsigned = "$headerPart.$payloadPart"
$hmac = [System.Security.Cryptography.HMACSHA256]::new([System.Text.Encoding]::UTF8.GetBytes($jwtSecret))
$signature = Encode-Base64Url($hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($unsigned)))
$forged = "$unsigned.$signature"

try {
    $badResp = Invoke-WebRequest -UseBasicParsing -Method GET -Uri 'http://localhost:8080/api/cart/count' -Headers @{ Authorization = "Bearer $forged" } -TimeoutSec $timeoutSec
    $badBody = $badResp.Content
    if ($badBody -is [byte[]]) {
        $badBody = [System.Text.Encoding]::UTF8.GetString($badBody)
    }
    Write-Output "WRONG_AUD_TOKEN_STATUS: $($badResp.StatusCode)"
    Write-Output "WRONG_AUD_TOKEN_BODY: $badBody"
    Write-Output 'WRONG_AUD_TOKEN_UNEXPECTED: accepted'
} catch {
    if ($_.Exception.Response) {
        $status = [int]$_.Exception.Response.StatusCode
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Output "WRONG_AUD_TOKEN_STATUS: $status"
        Write-Output "WRONG_AUD_TOKEN_BODY: $body"
    } else {
        Write-Output 'WRONG_AUD_TOKEN_STATUS: ERR'
    }
}
