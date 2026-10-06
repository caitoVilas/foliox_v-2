<#
.SYNOPSIS
  Start/stop the four Foliox Spring Boot services with the repo-root .env loaded.

.DESCRIPTION
  Loads .env (KEY=VALUE, comments/blanks ignored, optional quotes stripped) into the
  process environment BEFORE spawning java, so child processes inherit the secrets.
  Secrets have no defaults in application.yml — without this script (or a manually
  exported env) the jars fail fast with an unresolved placeholder error.

  .\scripts\services.ps1            Start services whose port is not yet listening,
                                    then wait (max 90s) for health and print a table.
  .\scripts\services.ps1 -Stop      Stop the 4 services (identified by listening port,
                                    java.exe only — other java processes are untouched).
  .\scripts\services.ps1 -Validate  Parse .env, show keys (values masked) + port status.
                                    Nothing is started or stopped.
#>
[CmdletBinding()]
param(
    [switch]$Stop,
    [switch]$Validate
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath  = Join-Path $repoRoot '.env'
$logsDir  = Join-Path $repoRoot 'logs'

$services = @(
    [pscustomobject]@{ Name = 'auth';        Port = 8081; Jar = 'foliox-auth\target\foliox-auth-0.1.0-SNAPSHOT.jar' }
    [pscustomobject]@{ Name = 'usuarios';    Port = 8082; Jar = 'foliox-usuarios\target\foliox-usuarios-0.1.0-SNAPSHOT.jar' }
    [pscustomobject]@{ Name = 'expedientes'; Port = 8083; Jar = 'foliox-expedientes\target\foliox-expedientes-0.1.0-SNAPSHOT.jar' }
    [pscustomobject]@{ Name = 'documentos';  Port = 8084; Jar = 'foliox-documentos\target\foliox-documentos-0.1.0-SNAPSHOT.jar' }
)

# Secrets the yml files require with NO fallback (fail-fast).
$requiredKeys = @('POSTGRES_PASSWORD', 'MINIO_ROOT_PASSWORD', 'FOLIOX_CLIENT_SECRET')

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw ".env not found at '$Path'. Copy the template and fill in real values:  Copy-Item .env.example .env"
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        $trim = $line.Trim()
        if ($trim -eq '' -or $trim.StartsWith('#')) { continue }

        $idx = $trim.IndexOf('=')
        if ($idx -lt 1) { continue }

        $key = $trim.Substring(0, $idx).Trim()
        $val = $trim.Substring($idx + 1).Trim()
        if ($val.Length -ge 2) {
            $first = $val[0]; $last = $val[$val.Length - 1]
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $val = $val.Substring(1, $val.Length - 2)
            }
        }
        [Environment]::SetEnvironmentVariable($key, $val, 'Process')
    }
}

function Get-ListeningPid {
    param([int]$Port)
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
            Select-Object -First 1
    if ($conn) { return $conn.OwningProcess }
    return $null
}

function Assert-RequiredKeys {
    $missing = $requiredKeys | Where-Object {
        [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, 'Process'))
    }
    if ($missing) {
        throw "Missing required variable(s) in .env: $($missing -join ', '). Set them before starting services."
    }
}

# ------------------------------ -Stop ------------------------------
if ($Stop) {
    foreach ($svc in $services) {
        $procId = Get-ListeningPid -Port $svc.Port
        if (-not $procId) {
            Write-Host ("{0,-12} port {1}  not listening (nothing to stop)" -f $svc.Name, $svc.Port)
            continue
        }
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $proc -or $proc.ProcessName -ne 'java') {
            Write-Warning ("port {0} is owned by '{1}' (PID {2}), not java - skipped." -f $svc.Port, $(if ($proc) { $proc.ProcessName } else { 'unknown' }), $procId)
            continue
        }
        # Ownership check: only kill java processes running OUR service jars,
        # never a colleague's/unrelated java app that happens to hold the port.
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $procId" -ErrorAction SilentlyContinue).CommandLine
        if (-not $cmdLine -or $cmdLine -notmatch 'foliox-[a-z]+[\\/]+target[\\/]+.+\.jar') {
            Write-Warning ("port {0} java (PID {1}) is not running a foliox service jar - skipped." -f $svc.Port, $procId)
            continue
        }
        Stop-Process -Id $procId -Force
        Write-Host ("{0,-12} port {1}  stopped (PID {2})" -f $svc.Name, $svc.Port, $procId)
    }
    return
}

# --------------------------- start / -Validate ---------------------------
try {
    Import-DotEnv -Path $envPath
} catch {
    Write-Error $_.Exception.Message
    exit 1
}

if ($Validate) {
    Write-Host ".env parsed from: $envPath"
    Write-Host "Keys found (values masked):"
    Get-ChildItem -Path Env: | Where-Object { $_.Name -match '^(POSTGRES|MINIO)_' } |
        Sort-Object Name |
        ForEach-Object { Write-Host ("  {0}={1}" -f $_.Name, $(if ([string]::IsNullOrWhiteSpace($_.Value)) { '<empty>' } else { '****' })) }
    $missing = $requiredKeys | Where-Object {
        [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, 'Process'))
    }
    if ($missing) { Write-Warning "Missing required keys: $($missing -join ', ')" }
    else { Write-Host "Required secret keys: present" }
    Write-Host "Port status:"
    foreach ($svc in $services) {
        $procId = Get-ListeningPid -Port $svc.Port
        Write-Host ("  {0,-12} :{1}  {2}" -f $svc.Name, $svc.Port, $(if ($procId) { "LISTENING (PID $procId)" } else { 'not listening' }))
    }
    exit 0
}

try {
    Assert-RequiredKeys
} catch {
    Write-Error $_.Exception.Message
    exit 1
}

New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
$started = @()
$skipped = @()

foreach ($svc in $services) {
    if (Get-ListeningPid -Port $svc.Port) {
        $skipped += $svc
        Write-Host ("skip  {0,-12} port {1} already listening" -f $svc.Name, $svc.Port)
        continue
    }

    $jarPath = Join-Path $repoRoot $svc.Jar
    if (-not (Test-Path -LiteralPath $jarPath)) {
        Write-Error ("jar not found for {0}: {1} (run mvn package first)" -f $svc.Name, $jarPath)
        exit 1
    }

    $outLog = Join-Path $logsDir "$($svc.Name).log"
    $errLog = Join-Path $logsDir "$($svc.Name).err.log"
    Start-Process -FilePath 'java' `
                  -ArgumentList @('-jar', $jarPath) `
                  -WorkingDirectory $repoRoot `
                  -RedirectStandardOutput $outLog `
                  -RedirectStandardError $errLog `
                  -WindowStyle Hidden | Out-Null
    $started += $svc
    Write-Host ("start {0,-12} -> logs\{1}.log / logs\{1}.err.log" -f $svc.Name, $svc.Name)
}

# Health wait: poll listening ports, up to 90s total for this phase.
$deadline = (Get-Date).AddSeconds(90)
do {
    $pending = @($services | Where-Object { -not (Get-ListeningPid -Port $_.Port) })
    if ($pending.Count -eq 0) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

Write-Host ""
$services | ForEach-Object {
    $procId = Get-ListeningPid -Port $_.Port
    [pscustomobject]@{
        Service = $_.Name
        Port    = $_.Port
        Status  = if ($procId) { 'LISTENING' } else { 'NOT LISTENING' }
        Pid     = if ($procId) { $procId } else { '-' }
    }
} | Format-Table -AutoSize | Out-String | Write-Host

if ($started.Count -gt 0) {
    Write-Host ("Started {0} service(s); skipped {1} already running. Logs: {2}" -f $started.Count, $skipped.Count, $logsDir)
}
$down = @($services | Where-Object { -not (Get-ListeningPid -Port $_.Port) })
if ($down.Count -gt 0) {
    Write-Warning ("Not up after 90s: {0}. Check logs\*.err.log" -f (($down | ForEach-Object { $_.Name }) -join ', '))
    exit 1
}
exit 0
