$ErrorActionPreference = "Stop"

$RepoDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "java-runtime.ps1")

Write-Host "Rebuilding opcoach-mcp-mail..."
Write-Host "Repository: $RepoDir"

Invoke-MailMcpMaven -Arguments @("clean", "package", "-DskipTests")

Write-Host
Write-Host "Build complete."
Write-Host "Jar: $RepoDir\target\opcoach-mcp-mail.jar"
