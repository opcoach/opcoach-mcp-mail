$ErrorActionPreference = "Stop"

$RepoDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "java-runtime.ps1")

$Jar = Join-Path $RepoDir "target\opcoach-mcp-mail.jar"
$BaseDir = if ($env:MAIL_MCP_BASE_DIR) { $env:MAIL_MCP_BASE_DIR } else { Join-Path $HOME ".opcoach-mcp-mail" }
$DefaultPort = if ($env:MAIL_MCP_PORT) { [int]$env:MAIL_MCP_PORT } else { 8095 }

function Prompt-MailMcpValue {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$DefaultValue
    )
    $value = Read-Host "$Label [$DefaultValue]"
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }
    return $value.Trim()
}

function ConvertTo-MailMcpProfileName {
    param([Parameter(Mandatory = $true)][string]$Value)
    $profile = $Value.ToLowerInvariant() -replace "[^a-z0-9]+", "-"
    $profile = $profile -replace "^-+", ""
    $profile = $profile -replace "-+$", ""
    if ([string]::IsNullOrWhiteSpace($profile)) {
        return "default"
    }
    return $profile
}

function Test-MailMcpPortFree {
    param([Parameter(Mandatory = $true)][int]$Port)
    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse("127.0.0.1"), $Port)
        $listener.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($listener) {
            $listener.Stop()
        }
    }
}

function Get-MailMcpFirstFreePort {
    param([Parameter(Mandatory = $true)][int]$StartPort)
    for ($port = $StartPort; $port -le 65535; $port++) {
        if (Test-MailMcpPortFree $port) {
            return $port
        }
    }
    throw "No free TCP port found."
}

function ConvertTo-MailMcpJavaPropertyValue {
    param([AllowNull()][string]$Value)
    if ($null -eq $Value) {
        return ""
    }
    $builder = [System.Text.StringBuilder]::new()
    for ($i = 0; $i -lt $Value.Length; $i++) {
        $char = $Value[$i]
        $code = [int][char]$char
        if ($code -eq 0x5c) {
            [void]$builder.Append("\\")
        } elseif ($code -eq 0x09) {
            [void]$builder.Append("\t")
        } elseif ($code -eq 0x0d) {
            [void]$builder.Append("\r")
        } elseif ($code -eq 0x0a) {
            [void]$builder.Append("\n")
        } elseif ($code -eq 0x20) {
            if ($i -eq 0) { [void]$builder.Append("\ ") } else { [void]$builder.Append(" ") }
        } elseif ($code -eq 0x3d) {
            [void]$builder.Append("\=")
        } elseif ($code -eq 0x3a) {
            [void]$builder.Append("\:")
        } elseif ($code -eq 0x23) {
            [void]$builder.Append("\#")
        } elseif ($code -eq 0x21) {
            [void]$builder.Append("\!")
        } elseif ($code -lt 0x20 -or $code -gt 0x7e) {
            [void]$builder.Append(("\u{0:x4}" -f $code))
        } else {
            [void]$builder.Append($char)
        }
    }
    return $builder.ToString()
}

function Write-MailMcpProperties {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][hashtable]$Values
    )
    $keys = @(
        "profile",
        "imap.host",
        "imap.port",
        "imap.security",
        "smtp.host",
        "smtp.port",
        "smtp.security",
        "username",
        "from.address",
        "from.name",
        "sent.mailbox"
    )
    $lines = foreach ($key in $keys) {
        "$key=$(ConvertTo-MailMcpJavaPropertyValue ([string]$Values[$key]))"
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $Path -Parent) | Out-Null
    Set-Content -Path $Path -Value $lines -Encoding ASCII
}

function ConvertFrom-MailMcpSecureString {
    param([Parameter(Mandatory = $true)][securestring]$Value)
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Value)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Register-MailMcpServer {
    param(
        [Parameter(Mandatory = $true)][string]$Profile,
        [Parameter(Mandatory = $true)][string]$ConfigFile,
        [Parameter(Mandatory = $true)][string]$RunDir,
        [Parameter(Mandatory = $true)][int]$Port
    )
    $registryDir = Join-Path $BaseDir "servers"
    New-Item -ItemType Directory -Force -Path $registryDir | Out-Null
    $registryName = $Profile -replace "[^A-Za-z0-9_.-]+", "_"
    if ([string]::IsNullOrWhiteSpace($registryName)) {
        $registryName = "default"
    }
    $serverFile = Join-Path $registryDir "$registryName.env"
    $lines = @(
        "PROFILE=$Profile",
        "CONFIG_FILE=$ConfigFile",
        "RUN_DIR=$RunDir",
        "HOST=127.0.0.1",
        "PORT=$Port"
    )
    Set-Content -Path $serverFile -Value $lines -Encoding UTF8
}

function Start-MailMcpServer {
    param(
        [Parameter(Mandatory = $true)][string]$Profile,
        [Parameter(Mandatory = $true)][string]$ConfigFile,
        [Parameter(Mandatory = $true)][string]$RunDir,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Password
    )

    Select-MailMcpJava
    New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
    $pidFile = Join-Path $RunDir "opcoach-mcp-mail.pid"
    $stdoutFile = Join-Path $RunDir "opcoach-mcp-mail.out.log"
    $stderrFile = Join-Path $RunDir "opcoach-mcp-mail.err.log"

    $oldConfig = $env:MAIL_MCP_CONFIG
    $oldRunDir = $env:MAIL_MCP_RUN_DIR
    $oldPassword = $env:MAIL_MCP_PASSWORD
    try {
        $env:MAIL_MCP_CONFIG = $ConfigFile
        $env:MAIL_MCP_RUN_DIR = $RunDir
        $env:MAIL_MCP_PASSWORD = $Password
        $processArgs = Join-MailMcpProcessArguments -Arguments @("-jar", $Jar, "--http", "--host", "127.0.0.1", "--port", "$Port", "--profile", $Profile)
        $process = Start-Process -FilePath $script:MailMcpJava -ArgumentList $processArgs -WorkingDirectory $RepoDir -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile -PassThru
        Set-Content -Path $pidFile -Value $process.Id -Encoding ASCII
        Start-Sleep -Seconds 1
        if ($process.HasExited) {
            Write-Host "Server failed to start. Last error lines:"
            if (Test-Path $stderrFile) {
                Get-Content $stderrFile -Tail 40
            }
            throw "opcoach-mcp-mail failed to start."
        }
    } finally {
        $env:MAIL_MCP_CONFIG = $oldConfig
        $env:MAIL_MCP_RUN_DIR = $oldRunDir
        $env:MAIL_MCP_PASSWORD = $oldPassword
    }
}

Write-Host "opcoach-mcp-mail Windows local setup"
Write-Host
Select-MailMcpJava
Write-Host "Using Java: $script:MailMcpJava"

if (-not (Test-Path $Jar)) {
    Write-Host "Building the server with Maven Wrapper..."
    Invoke-MailMcpWithJava (Join-Path $RepoDir "mvnw.cmd") @("-DskipTests", "package")
}

$defaultProfile = ConvertTo-MailMcpProfileName $(if ($env:USERNAME) { $env:USERNAME } else { "default" })
$profile = ConvertTo-MailMcpProfileName (Prompt-MailMcpValue "Profile name" $defaultProfile)
$suggestedPort = Get-MailMcpFirstFreePort $DefaultPort

while ($true) {
    $portValue = Prompt-MailMcpValue "Local MCP port" "$suggestedPort"
    $port = 0
    if ([int]::TryParse($portValue, [ref]$port) -and $port -ge 1 -and $port -le 65535) {
        if (Test-MailMcpPortFree $port) {
            break
        }
        Write-Host "Port $port is already in use. Choose another one."
    } else {
        Write-Host "Invalid port."
    }
}

$configFile = Join-Path $BaseDir "profiles\$profile.properties"
$runDir = Join-Path $BaseDir "run\$profile"

Write-Host
Write-Host "Mailbox settings"
$imapHost = Prompt-MailMcpValue "IMAP host" "imap.example.com"
$imapPort = Prompt-MailMcpValue "IMAP port" "993"
$imapSecurity = Prompt-MailMcpValue "IMAP security (ssl_tls, starttls, none)" "ssl_tls"
$smtpHost = Prompt-MailMcpValue "SMTP host" "smtp.example.com"
$smtpPort = Prompt-MailMcpValue "SMTP port" "465"
$smtpSecurity = Prompt-MailMcpValue "SMTP security (ssl_tls, starttls, none)" "ssl_tls"
$username = Prompt-MailMcpValue "Email username" "training@example.com"
$fromAddress = Prompt-MailMcpValue "Sender address" $username
$fromName = Prompt-MailMcpValue "Sender name" "MCP Training"
$sentMailbox = Prompt-MailMcpValue "Sent folder" "INBOX.Sent"
$securePassword = Read-Host "Password or app password" -AsSecureString
$password = ConvertFrom-MailMcpSecureString $securePassword

Write-MailMcpProperties $configFile @{
    "profile" = $profile
    "imap.host" = $imapHost
    "imap.port" = $imapPort
    "imap.security" = $imapSecurity
    "smtp.host" = $smtpHost
    "smtp.port" = $smtpPort
    "smtp.security" = $smtpSecurity
    "username" = $username
    "from.address" = $fromAddress
    "from.name" = $fromName
    "sent.mailbox" = $sentMailbox
}

Register-MailMcpServer $profile $configFile $runDir $port
Start-MailMcpServer $profile $configFile $runDir $port $password

Write-Host
Write-Host "Done."
Write-Host "MCP URL:    http://127.0.0.1:$port/mcp"
Write-Host "Health URL: http://127.0.0.1:$port/health"
Write-Host
Write-Host "After a reboot, restart registered servers with:"
Write-Host ".\bin\start-all.ps1"
