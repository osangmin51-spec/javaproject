param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ScriptPath = Join-Path $Root "scripts\start-server.ps1"
$TaskName = "JavaMockStockServer"
$Argument = "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`" -Port $Port"

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $Argument -WorkingDirectory $Root
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DisallowStartIfOnBatteries:$false -MultipleInstances IgnoreNew

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Start Java mock stock investment web server at Windows logon." `
    -Force | Out-Null

Write-Host "Installed Windows startup task: $TaskName"
Write-Host "It will start http://localhost:$Port/ after Windows logon."
