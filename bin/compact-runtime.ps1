$ErrorActionPreference = "Stop"

$RepoDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "java-runtime.ps1")

if (-not (Test-MailMcpJdkHomeCompatible $script:MailMcpJdkDir)) {
    Write-Host "No local build JDK found under $script:MailMcpJdkDir."
    Write-Host "Nothing to compact."
    exit 0
}

$jdkSize = Get-MailMcpDirectorySizeMb $script:MailMcpJdkDir
Write-Host "Local build JDK size: about $jdkSize MB."
New-MailMcpCompactRuntime
$runtimeSize = Get-MailMcpDirectorySizeMb $script:MailMcpCompactRuntimeDir
Remove-MailMcpLocalJdk

Write-Host "Removed the local build JDK."
Write-Host "Compact runtime ready: $script:MailMcpCompactRuntimeDir ($runtimeSize MB)."
