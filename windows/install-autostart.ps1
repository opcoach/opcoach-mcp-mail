$ErrorActionPreference = 'Stop'

$taskName = 'OPCoach MCP Mail'
$repositoryDirectory = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $repositoryDirectory 'target\opcoach-mcp-mail.jar'

if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "Application JAR not found: $jarPath. Run mvnw.cmd -DskipTests package first."
}

$javaw = Get-Command 'javaw.exe' -ErrorAction SilentlyContinue
if ($null -eq $javaw) {
    $java = Get-Command 'java.exe' -ErrorAction SilentlyContinue
    if ($null -eq $java) {
        throw 'Java was not found. Install Temurin JDK 24 and reopen this window.'
    }
    $javaExecutable = $java.Source
} else {
    $javaExecutable = $javaw.Source
}

$arguments = '-jar "{0}" web-manager --start-registered --no-open' -f $jarPath
$action = New-ScheduledTaskAction `
    -Execute $javaExecutable `
    -Argument $arguments `
    -WorkingDirectory $repositoryDirectory
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal `
    -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) `
    -LogonType Interactive `
    -RunLevel Limited
$task = New-ScheduledTask -Action $action -Trigger $trigger -Settings $settings -Principal $principal

Register-ScheduledTask -TaskName $taskName -InputObject $task -Force | Out-Null

Write-Host "Automatic startup installed for $($env:USERNAME)." -ForegroundColor Green
Write-Host 'MCP Mail will start automatically after this Windows user signs in.'
Write-Host "Registered profiles will be started from $repositoryDirectory."
