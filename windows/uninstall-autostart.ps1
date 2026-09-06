$ErrorActionPreference = 'Stop'

$taskName = 'OPCoach MCP Mail'
$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($null -eq $task) {
    Write-Host 'Automatic startup is not installed.'
    exit 0
}

Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
Write-Host 'Automatic startup removed.' -ForegroundColor Green
