Set-Location "c:\Users\Siddhesh Akole\OneDrive\Documents\Desktop\Hack-1-\printease"
$ErrorActionPreference = 'Stop'

$email = if ($env.E2E_EMAIL) { $env.E2E_EMAIL } else { 'test@example.com' }
$pass = if ($env.E2E_PASSWORD) { $env.E2E_PASSWORD } else { 'ChangeMe123!' }
$base = 'http://localhost:8080'

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [hashtable]$Headers = $null,
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session = $null
    )

    $params = @{ Method = $Method; Uri = $Url; ErrorAction = 'Stop'; UseBasicParsing = $true }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    if ($null -ne $Headers) { $params.Headers = $Headers }
    if ($null -ne $Session) { $params.WebSession = $Session }

    Invoke-WebRequest @params
}

function Parse-JsonContent {
    param([Parameter(Mandatory = $true)]$Content)
    if ($Content -is [byte[]]) {
        return ([System.Text.Encoding]::UTF8.GetString($Content) | ConvertFrom-Json)
    }
    return ([string]$Content | ConvertFrom-Json)
}

$results = New-Object System.Collections.Generic.List[object]

$healthTargets = @(
    @{ name = 'gateway'; url = 'http://localhost:8080/actuator/health' }
)

foreach ($h in $healthTargets) {
    try {
        $r = Invoke-WebRequest -Method GET -Uri $h.url -TimeoutSec 20 -UseBasicParsing
        $results.Add([pscustomobject]@{
                test   = "health:$($h.name)"
                status = $r.StatusCode
                pass   = ($r.StatusCode -eq 200)
                detail = 'ok'
            })
    }
    catch {
        $results.Add([pscustomobject]@{
                test   = "health:$($h.name)"
                status = 'ERR'
                pass   = $false
                detail = $_.Exception.Message
            })
    }
}

$blockedPorts = @(
    @{ name = 'auth'; url = 'http://localhost:8081/actuator/health' },
    @{ name = 'objects'; url = 'http://localhost:8082/actuator/health' },
    @{ name = 'cart'; url = 'http://localhost:8083/actuator/health' },
    @{ name = 'checkout'; url = 'http://localhost:8084/actuator/health' },
    @{ name = 'orders'; url = 'http://localhost:8085/actuator/health' },
    @{ name = 'otp'; url = 'http://localhost:8086/actuator/health' },
    @{ name = 'transactions'; url = 'http://localhost:8087/actuator/health' },
    @{ name = 'shops'; url = 'http://localhost:8088/actuator/health' }
)

foreach ($p in $blockedPorts) {
    try {
        $r = Invoke-WebRequest -Method GET -Uri $p.url -TimeoutSec 10 -UseBasicParsing
        $results.Add([pscustomobject]@{
                test   = "direct_port_blocked:$($p.name)"
                status = $r.StatusCode
                pass   = $false
                detail = 'Unexpectedly reachable from host'
            })
    }
    catch {
        $results.Add([pscustomobject]@{
                test   = "direct_port_blocked:$($p.name)"
                status = 'BLOCKED'
                pass   = $true
                detail = 'Blocked as expected (gateway-only exposure)'
            })
    }
}

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginBody = @{ email = $email; password = $pass }
$token = $null
$userId = $null

try {
    $loginResp = Invoke-Json -Method POST -Url "$base/auth/login" -Body $loginBody -Session $session
    $loginJson = Parse-JsonContent -Content $loginResp.Content
    $token = $loginJson.data.accessToken
    $userId = $loginJson.data.userId
    $results.Add([pscustomobject]@{
            test   = 'auth:login_initial'
            status = $loginResp.StatusCode
            pass   = $true
            detail = $loginJson.message
        })
}
catch {
    $results.Add([pscustomobject]@{
            test   = 'auth:login_initial'
            status = 'ERR'
            pass   = $false
            detail = $_.Exception.Message
        })
}

if (-not $token) {
    try {
        $registerBody = @{
            firstName = 'Siddhesh'
            lastName  = 'Akole'
            email     = $email
            password  = $pass
        }
        $regResp = Invoke-Json -Method POST -Url "$base/auth/register" -Body $registerBody -Session $session
        $regJson = Parse-JsonContent -Content $regResp.Content
        $userId = $regJson.data.userId
        $results.Add([pscustomobject]@{
                test   = 'auth:register'
                status = $regResp.StatusCode
                pass   = ($regResp.StatusCode -eq 201)
                detail = $regJson.message
            })
    }
    catch {
        $results.Add([pscustomobject]@{
                test   = 'auth:register'
                status = 'ERR'
                pass   = $false
                detail = $_.Exception.Message
            })
    }

    if (-not $userId) {
        try {
            $userId = (docker exec -i qprint-postgres psql -U QPrint -d qprint -At -c "select id from users where email='$email' order by created_at desc limit 1;").Trim()
            if ($userId) {
                $results.Add([pscustomobject]@{
                        test   = 'auth:user_lookup'
                        status = 200
                        pass   = $true
                        detail = 'Resolved user ID from database'
                    })
            }
        }
        catch {
            $results.Add([pscustomobject]@{
                    test   = 'auth:user_lookup'
                    status = 'ERR'
                    pass   = $false
                    detail = $_.Exception.Message
                })
        }
    }

    if ($userId) {
        $otp = $null
        try {
            $otp = (docker exec -i qprint-postgres psql -U QPrint -d qprint -At -c "select code from email_verification_codes where user_id='$userId' and used=false and expires_at > now() order by created_at desc limit 1;").Trim()
        }
        catch {}

        if (-not $otp) {
            Start-Sleep -Seconds 2
            $otpLine = docker compose logs --tail=200 qprint-auth | Select-String -Pattern "DEV OTP for ${email}:" | Select-Object -Last 1
            if ($otpLine) {
                $otp = [regex]::Match($otpLine.Line, '([0-9]{6})').Groups[1].Value
            }
        }

        if ($otp) {
            try {
                $verResp = Invoke-Json -Method POST -Url "$base/auth/verify-email" -Body @{ userId = $userId; code = $otp } -Session $session
                $verJson = Parse-JsonContent -Content $verResp.Content
                $results.Add([pscustomobject]@{
                        test   = 'auth:verify_email'
                        status = $verResp.StatusCode
                        pass   = $true
                        detail = $verJson.message
                    })
            }
            catch {
                $results.Add([pscustomobject]@{
                        test   = 'auth:verify_email'
                        status = 'ERR'
                        pass   = $false
                        detail = $_.Exception.Message
                    })
            }
        }
        else {
            $results.Add([pscustomobject]@{
                    test   = 'auth:verify_email'
                    status = 'ERR'
                    pass   = $false
                    detail = 'OTP not found in DB or logs'
                })
        }

        try {
            $loginResp2 = Invoke-Json -Method POST -Url "$base/auth/login" -Body $loginBody -Session $session
            $loginJson2 = Parse-JsonContent -Content $loginResp2.Content
            $token = $loginJson2.data.accessToken
            $results.Add([pscustomobject]@{
                    test   = 'auth:login_after_verify'
                    status = $loginResp2.StatusCode
                    pass   = (-not [string]::IsNullOrEmpty($token))
                    detail = $loginJson2.message
                })
        }
        catch {
            $results.Add([pscustomobject]@{
                    test   = 'auth:login_after_verify'
                    status = 'ERR'
                    pass   = $false
                    detail = $_.Exception.Message
                })
        }
    }
}

if ($token) {
    $headers = @{ Authorization = "Bearer $token" }
    $protected = @(
        @{ name = 'auth_me'; method = 'GET'; url = "$base/auth/me"; body = $null },
        @{ name = 'cart_get'; method = 'GET'; url = "$base/api/cart"; body = $null },
        @{ name = 'cart_count'; method = 'GET'; url = "$base/api/cart/count"; body = $null },
        @{ name = 'objects_get_random'; method = 'GET'; url = "$base/api/objects/00000000-0000-0000-0000-000000000000"; body = $null },
        @{ name = 'orders_list'; method = 'GET'; url = "$base/api/orders"; body = $null },
        @{ name = 'orders_active'; method = 'GET'; url = "$base/api/orders/active"; body = $null },
        @{ name = 'transactions_list'; method = 'GET'; url = "$base/api/transactions"; body = $null },
        @{ name = 'shops_nearby'; method = 'GET'; url = "$base/api/shops/nearby"; body = $null },
        @{ name = 'checkout_initiate'; method = 'POST'; url = "$base/api/checkout/initiate"; body = @{} }
    )

    foreach ($p in $protected) {
        try {
            $resp = Invoke-Json -Method $p.method -Url $p.url -Body $p.body -Headers $headers -Session $session
            $msg = ''
            try {
                $j = Parse-JsonContent -Content $resp.Content
                $msg = $j.message
            }
            catch {
                $msg = 'ok'
            }
            $results.Add([pscustomobject]@{
                    test   = "api:$($p.name)"
                    status = $resp.StatusCode
                    pass   = $true
                    detail = $msg
                })
        }
        catch {
            $code = 'ERR'
            if ($_.Exception.Response) {
                try { $code = [int]$_.Exception.Response.StatusCode } catch {}
            }
            $isPass = ($code -notin 401, 403, 404, 'ERR')
            $results.Add([pscustomobject]@{
                    test   = "api:$($p.name)"
                    status = $code
                    pass   = $isPass
                    detail = $_.Exception.Message
                })
        }
    }

    try {
        $refreshResp = Invoke-Json -Method POST -Url "$base/auth/refresh" -Session $session
        $refreshJson = Parse-JsonContent -Content $refreshResp.Content
        $results.Add([pscustomobject]@{
                test   = 'auth:refresh'
                status = $refreshResp.StatusCode
                pass   = ($refreshResp.StatusCode -eq 200 -and $refreshJson.success -eq $true)
                detail = $refreshJson.message
            })
    }
    catch {
        $results.Add([pscustomobject]@{
                test   = 'auth:refresh'
                status = 'ERR'
                pass   = $false
                detail = $_.Exception.Message
            })
    }

    try {
        $logoutResp = Invoke-Json -Method POST -Url "$base/auth/logout" -Session $session
        $logoutJson = Parse-JsonContent -Content $logoutResp.Content
        $results.Add([pscustomobject]@{
                test   = 'auth:logout'
                status = $logoutResp.StatusCode
                pass   = ($logoutResp.StatusCode -eq 200)
                detail = $logoutJson.message
            })
    }
    catch {
        $results.Add([pscustomobject]@{
                test   = 'auth:logout'
                status = 'ERR'
                pass   = $false
                detail = $_.Exception.Message
            })
    }
}
else {
    $results.Add([pscustomobject]@{
            test   = 'auth:token_available'
            status = 'ERR'
            pass   = $false
            detail = 'No access token available; protected route tests skipped'
        })
}

$passCount = ($results | Where-Object { $_.pass -eq $true }).Count
$total = $results.Count
"TEST_SUMMARY $passCount/$total"
$results | Format-Table -AutoSize
