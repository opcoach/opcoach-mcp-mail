param([switch]$Wait)

$ErrorActionPreference = "Stop"

$RepoDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "java-runtime.ps1")

$Jar = Join-Path $RepoDir "target\opcoach-mcp-mail.jar"

if (-not (Test-Path $Jar)) {
    Write-Host "Building the server with local Maven..."
    Invoke-MailMcpMaven -Arguments @("-DskipTests", "package")
}

$javaLauncher = Get-MailMcpGuiJava
$processArgs = Join-MailMcpProcessArguments -Arguments @("-jar", $Jar, "manager")

if ($Wait) {
    Start-Process -FilePath $javaLauncher -ArgumentList $processArgs -WorkingDirectory $RepoDir -Wait
} else {
    Start-Process -FilePath $javaLauncher -ArgumentList $processArgs -WorkingDirectory $RepoDir
}
