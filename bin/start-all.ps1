$ErrorActionPreference = "Stop"

$RepoDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "java-runtime.ps1")

$Jar = Join-Path $RepoDir "target\opcoach-mcp-mail.jar"
$BaseDir = if ($env:MAIL_MCP_BASE_DIR) { $env:MAIL_MCP_BASE_DIR } else { Join-Path $HOME ".opcoach-mcp-mail" }
$RegistryDir = Join-Path $BaseDir "servers"

function ConvertFrom-MailMcpSecureString {
    param([Parameter(Mandatory = $true)][securestring]$Value)
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Read-MailMcpServerFile {
    param([Parameter(Mandatory = $true)][string]$Path)
    $values = @{}
    foreach ($line in Get-Content $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }
        $index = $line.IndexOf("=")
        $key = $line.Substring(0, $index)
        $value = $line.Substring($index + 1)
        $values[$key] = $value
    }
    return $values
}

function Start-MailMcpServer {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Server,
        [Parameter(Mandatory = $true)][string]$Password
    )

    Select-MailMcpJava
    $profile = $Server["PROFILE"]
    $configFile = $Server["CONFIG_FILE"]
    $runDir = $Server["RUN_DIR"]
    $hostName = if ($Server["HOST"]) { $Server["HOST"] } else { "127.0.0.1" }
    $port = $Server["PORT"]

    if (-not (Test-Path $configFile)) {
        throw "Missing configuration for profile ${profile}: $configFile"
    }

    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    $pidFile = Join-Path $runDir "opcoach-mcp-mail.pid"
    $stdoutFile = Join-Path $runDir "opcoach-mcp-mail.out.log"
    $stderrFile = Join-Path $runDir "opcoach-mcp-mail.err.log"

    $oldConfig = $env:MAIL_MCP_CONFIG
    $oldRunDir = $env:MAIL_MCP_RUN_DIR
    $oldPassword = $env:MAIL_MCP_PASSWORD
    try {
        $env:MAIL_MCP_CONFIG = $configFile
        $env:MAIL_MCP_RUN_DIR = $runDir
        $env:MAIL_MCP_PASSWORD = $Password
        $processArgs = Join-MailMcpProcessArguments -Arguments @("-jar", $Jar, "--http", "--host", $hostName, "--port", "$port", "--profile", $profile)
        $javaLauncher = Get-MailMcpGuiJava
        $process = Start-Process -FilePath $javaLauncher -ArgumentList $processArgs -WorkingDirectory $RepoDir -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile -PassThru
        Set-Content -Path $pidFile -Value $process.Id -Encoding ASCII
        Start-Sleep -Seconds 1
        if ($process.HasExited) {
            Write-Host "Server failed to start. Last error lines:"
            if (Test-Path $stderrFile) {
                Get-Content $stderrFile -Tail 40
            }
            throw "opcoach-mcp-mail failed to start for profile $profile."
        }
        Write-Host "Started profile $profile on http://${hostName}:$port/mcp"
    } finally {
        $env:MAIL_MCP_CONFIG = $oldConfig
        $env:MAIL_MCP_RUN_DIR = $oldRunDir
        $env:MAIL_MCP_PASSWORD = $oldPassword
    }
}

Select-MailMcpJava
Write-Host "Using Java: $script:MailMcpJava"

if (-not (Test-Path $Jar)) {
    Write-Host "Building the server with local Maven..."
    Invoke-MailMcpMaven -Arguments @("-DskipTests", "package")
}

if (-not (Test-Path $RegistryDir)) {
    Write-Host "No registered local MCP mail servers found in $RegistryDir."
    Write-Host "Run .\bin\local-wizard.ps1 once per mailbox first."
    exit 0
}

$serverFiles = Get-ChildItem -Path $RegistryDir -Filter "*.env"
if ($serverFiles.Count -eq 0) {
    Write-Host "No registered local MCP mail servers found in $RegistryDir."
    Write-Host "Run .\bin\local-wizard.ps1 once per mailbox first."
    exit 0
}

foreach ($serverFile in $serverFiles) {
    $server = Read-MailMcpServerFile $serverFile.FullName
    $profile = $server["PROFILE"]
    $securePassword = Read-Host "Password or app password for profile $profile" -AsSecureString
    $password = ConvertFrom-MailMcpSecureString $securePassword
    Start-MailMcpServer $server $password
}
