param(
    [int]$Port = 8080,
    [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$listenerLine = netstat -ano | Select-String ":$Port\s+.*LISTENING" | Select-Object -First 1
if ($listenerLine) {
    $serverPid = (($listenerLine.ToString().Trim() -split "\s+")[-1])
    Write-Host "MiniProjectApp is already running on http://localhost:$Port/ (PID $serverPid)"
    if ($OpenBrowser) {
        Start-Process "http://localhost:$Port/"
    }
    exit 0
}

if (-not (Test-Path out)) {
    New-Item -ItemType Directory -Force out | Out-Null
}

$sources = Get-ChildItem -Path "src\main\java" -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }

javac -encoding UTF-8 -d out $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$launcher = Join-Path $Root "scripts\server-launcher.cmd"
$commandLine = "cmd.exe /c start `"MiniProjectApp`" /min `"$launcher`" $Port"
try {
    $result = Invoke-CimMethod `
        -ClassName Win32_Process `
        -MethodName Create `
        -Arguments @{ CommandLine = $commandLine; CurrentDirectory = $Root }

    if ($result.ReturnValue -ne 0) {
        throw "Win32_Process.Create returned $($result.ReturnValue)."
    }
} catch {
    $processInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $processInfo.FileName = $launcher
    $processInfo.Arguments = "$Port"
    $processInfo.WorkingDirectory = $Root
    $processInfo.UseShellExecute = $true
    $processInfo.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
    [System.Diagnostics.Process]::Start($processInfo) | Out-Null
}

Start-Sleep -Seconds 2

try {
    $status = (Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$Port/" -TimeoutSec 5).StatusCode
    Write-Host "MiniProjectApp started: http://localhost:$Port/ (HTTP $status)"
    if ($OpenBrowser) {
        Start-Process "http://localhost:$Port/"
    }
} catch {
    Write-Host "MiniProjectApp was started, but the HTTP check did not finish yet. See server.out.log."
}
