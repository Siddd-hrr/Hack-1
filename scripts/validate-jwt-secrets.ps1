param(
  [string]$DevEnvPath = ".env.dev",
  [string]$StagingEnvPath = "deploy/.env.staging",
  [string]$ProdEnvPath = "deploy/.env.prod"
)

function Get-EnvValue {
  param(
    [string]$FilePath,
    [string]$Key
  )

  if (-not (Test-Path $FilePath)) {
    throw "Missing env file: $FilePath"
  }

  $line = Get-Content $FilePath | Where-Object {
    $_ -match "^\s*$Key\s*="
  } | Select-Object -First 1

  if (-not $line) {
    throw "Missing key '$Key' in $FilePath"
  }

  $value = ($line -split "=", 2)[1].Trim()
  if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
    $value = $value.Substring(1, $value.Length - 2)
  }

  return $value
}

try {
  $devSecret = Get-EnvValue -FilePath $DevEnvPath -Key "JWT_SECRET"
  $stagingSecret = Get-EnvValue -FilePath $StagingEnvPath -Key "JWT_SECRET"
  $prodSecret = Get-EnvValue -FilePath $ProdEnvPath -Key "JWT_SECRET"

  foreach ($pair in @(
    @{ Left = "dev"; Right = "staging"; A = $devSecret; B = $stagingSecret },
    @{ Left = "dev"; Right = "prod"; A = $devSecret; B = $prodSecret },
    @{ Left = "staging"; Right = "prod"; A = $stagingSecret; B = $prodSecret }
  )) {
    if ($pair.A -eq $pair.B) {
      throw "JWT_SECRET is reused between $($pair.Left) and $($pair.Right). Use unique secrets per environment."
    }
  }

  foreach ($item in @(
    @{ Name = "dev"; Value = $devSecret },
    @{ Name = "staging"; Value = $stagingSecret },
    @{ Name = "prod"; Value = $prodSecret }
  )) {
    if ([string]::IsNullOrWhiteSpace($item.Value)) {
      throw "JWT_SECRET is empty for $($item.Name)."
    }
    if ($item.Value.Length -lt 43) {
      Write-Warning "JWT_SECRET for $($item.Name) looks short. Use at least a 32-byte random key (base64 is usually >= 43 chars)."
    }
  }

  Write-Output "JWT secret separation check passed. dev/staging/prod secrets are unique."
  exit 0
}
catch {
  Write-Error $_.Exception.Message
  exit 1
}
