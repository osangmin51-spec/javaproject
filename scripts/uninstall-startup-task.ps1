$TaskName = "JavaMockStockServer"

if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
    Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
    Write-Host "Removed Windows startup task: $TaskName"
} else {
    Write-Host "Startup task is not installed: $TaskName"
}
