param(
    [int]$Port = 8080
)

$listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if (-not $listeners) {
    Write-Host "No server is listening on port $Port."
    exit 0
}

$listeners |
    Select-Object -ExpandProperty OwningProcess -Unique |
    ForEach-Object {
        $process = Get-Process -Id $_ -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -eq "java") {
            Stop-Process -Id $process.Id -Force
            Write-Host "Stopped Java server on port $Port (PID $($process.Id))."
        } else {
            Write-Host "Port $Port is used by PID $_, but it is not a java process. Skipped."
        }
    }
