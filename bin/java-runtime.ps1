$ErrorActionPreference = "Stop"

if (-not $script:RepoDir) {
    $script:RepoDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$script:MailMcpRequiredJava = 24
if ($env:MAIL_MCP_REQUIRED_JAVA) {
    $script:MailMcpRequiredJava = [int]$env:MAIL_MCP_REQUIRED_JAVA
}

$script:MailMcpRuntimeDir = Join-Path $script:RepoDir ".runtime"
if ($env:MAIL_MCP_RUNTIME_DIR) {
    $script:MailMcpRuntimeDir = $env:MAIL_MCP_RUNTIME_DIR
}

$script:MailMcpJdkDir = Join-Path $script:MailMcpRuntimeDir "temurin-jdk-$script:MailMcpRequiredJava"
$script:MailMcpCompactRuntimeDir = Join-Path $script:MailMcpRuntimeDir "mail-mcp-runtime-$script:MailMcpRequiredJava"
$script:MailMcpMavenDir = Join-Path $script:MailMcpRuntimeDir "maven"

function Invoke-MailMcpNativeOutput {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string]$Arguments = ""
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    [void]$process.Start()
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    $process.WaitForExit()

    return (($stdout + "`n" + $stderr) -split "\r?\n" | Where-Object { $_ })
}

function Get-MailMcpJavaMajor {
    param([Parameter(Mandatory = $true)][string]$JavaPath)

    if (-not (Test-Path $JavaPath)) {
        return $null
    }

    $output = Invoke-MailMcpNativeOutput -FilePath $JavaPath -Arguments "-version"
    foreach ($line in $output) {
        if ($line -match 'version "([0-9]+)') {
            return [int]$Matches[1]
        }
    }
    return $null
}

function Test-MailMcpJavaCompatible {
    param([Parameter(Mandatory = $true)][string]$JavaPath)

    $major = Get-MailMcpJavaMajor $JavaPath
    return ($null -ne $major -and $major -ge $script:MailMcpRequiredJava)
}

function Test-MailMcpJdkHomeCompatible {
    param([Parameter(Mandatory = $true)][string]$JavaHome)

    $java = Join-Path $JavaHome "bin\java.exe"
    $javac = Join-Path $JavaHome "bin\javac.exe"
    $jlink = Join-Path $JavaHome "bin\jlink.exe"
    return ((Test-MailMcpJavaCompatible $java) -and
        (Test-Path $javac) -and
        (Test-Path $jlink))
}

function Set-MailMcpJavaHome {
    param([Parameter(Mandatory = $true)][string]$JavaHome)

    $script:MailMcpJavaHome = $JavaHome
    $script:MailMcpJava = Join-Path $JavaHome "bin\java.exe"
}

function Get-MailMcpDirectorySizeMb {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path $Path)) {
        return 0
    }

    $sum = (Get-ChildItem -LiteralPath $Path -Recurse -File -ErrorAction SilentlyContinue |
        Measure-Object -Property Length -Sum).Sum
    if ($null -eq $sum) {
        return 0
    }
    return [math]::Round($sum / 1MB, 1)
}

function Get-MailMcpWindowsArch {
    $arch = $env:PROCESSOR_ARCHITECTURE
    if ($env:PROCESSOR_ARCHITEW6432) {
        $arch = $env:PROCESSOR_ARCHITEW6432
    }

    switch -Regex ($arch) {
        "AMD64|x86_64" { return "x64" }
        "ARM64|AARCH64" { return "aarch64" }
        default { throw "Unsupported Windows CPU architecture: $arch" }
    }
}

function Install-MailMcpJdk {
    $arch = Get-MailMcpWindowsArch
    $downloadDir = Join-Path $script:MailMcpRuntimeDir "downloads"
    $tmpDir = Join-Path $script:MailMcpRuntimeDir "tmp\temurin-jdk-$script:MailMcpRequiredJava-windows-$arch"
    $archive = Join-Path $downloadDir "temurin-jdk-$script:MailMcpRequiredJava-windows-$arch.zip"
    $url = "https://api.adoptium.net/v3/binary/latest/$script:MailMcpRequiredJava/ga/windows/$arch/jdk/hotspot/normal/eclipse?project=jdk"

    New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $tmpDir -Parent) | Out-Null
    if (Test-Path $tmpDir) {
        Remove-Item -Recurse -Force $tmpDir
    }
    if (Test-Path $script:MailMcpJdkDir) {
        Remove-Item -Recurse -Force $script:MailMcpJdkDir
    }
    New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

    Write-Host "Compatible Java $script:MailMcpRequiredJava was not found. Downloading a local Eclipse Temurin JDK..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    } catch {
        # PowerShell 7+ already negotiates modern TLS by default.
    }
    Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing
    Expand-Archive -Path $archive -DestinationPath $tmpDir -Force

    $javaExe = Get-ChildItem -Path $tmpDir -Filter "java.exe" -Recurse |
        Where-Object { $_.FullName -match "\\bin\\java\.exe$" } |
        Select-Object -First 1

    if (-not $javaExe) {
        throw "Downloaded Java archive does not contain bin\java.exe."
    }

    $javaHome = Split-Path (Split-Path $javaExe.FullName -Parent) -Parent
    Move-Item -Path $javaHome -Destination $script:MailMcpJdkDir
    Remove-Item -Recurse -Force $tmpDir
}

function Get-MailMcpWrapperProperties {
    $propertiesFile = Join-Path $script:RepoDir ".mvn\wrapper\maven-wrapper.properties"
    if (-not (Test-Path $propertiesFile)) {
        throw "Missing Maven Wrapper properties file: $propertiesFile"
    }
    return Get-Content -Raw $propertiesFile | ConvertFrom-StringData
}

function Get-MailMcpFileNameFromUrl {
    param([Parameter(Mandatory = $true)][string]$Url)

    $uri = [System.Uri]$Url
    return [System.IO.Path]::GetFileName($uri.AbsolutePath)
}

function Get-MailMcpMavenDistributionName {
    param([Parameter(Mandatory = $true)][string]$DistributionUrl)

    $name = Get-MailMcpFileNameFromUrl $DistributionUrl
    $mainName = $name -replace "\.[^.]*$", ""
    $mainName = $mainName -replace "-bin$", ""
    if ([string]::IsNullOrWhiteSpace($mainName) -or $name -eq $mainName) {
        throw "Invalid Maven distribution URL in Maven Wrapper properties: $DistributionUrl"
    }
    return $mainName
}

function Install-MailMcpMaven {
    $properties = Get-MailMcpWrapperProperties
    $distributionUrl = $properties.distributionUrl
    if ([string]::IsNullOrWhiteSpace($distributionUrl)) {
        throw "Missing distributionUrl in Maven Wrapper properties."
    }
    if ($env:MVNW_REPOURL) {
        $distributionUrl = "$env:MVNW_REPOURL/org/apache/maven/" + ($distributionUrl -replace "^.*/org/apache/maven/", "")
    }

    $distributionName = Get-MailMcpMavenDistributionName $distributionUrl
    $targetDir = Join-Path $script:MailMcpMavenDir $distributionName
    $downloadDir = Join-Path $script:MailMcpRuntimeDir "downloads"
    $tmpDir = Join-Path $script:MailMcpRuntimeDir "tmp\$distributionName"
    $archive = Join-Path $downloadDir (Get-MailMcpFileNameFromUrl $distributionUrl)

    if ((Test-Path (Join-Path $targetDir "bin\m2.conf")) -and
        (Get-ChildItem -Path (Join-Path $targetDir "boot") -Filter "plexus-classworlds-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1)) {
        return $targetDir
    }

    New-Item -ItemType Directory -Force -Path $downloadDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path $tmpDir -Parent) | Out-Null
    New-Item -ItemType Directory -Force -Path $script:MailMcpMavenDir | Out-Null
    if (Test-Path $tmpDir) {
        Remove-Item -Recurse -Force $tmpDir
    }
    if (Test-Path $targetDir) {
        Remove-Item -Recurse -Force $targetDir
    }
    New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

    Write-Host "Local Maven was not found. Downloading $distributionName..."
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    } catch {
        # PowerShell 7+ already negotiates modern TLS by default.
    }
    Invoke-WebRequest -Uri $distributionUrl -OutFile $archive -UseBasicParsing
    Expand-Archive -Path $archive -DestinationPath $tmpDir -Force

    $mavenDir = Get-ChildItem -Path $tmpDir -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\m2.conf") } |
        Select-Object -First 1

    if (-not $mavenDir) {
        throw "Downloaded Maven archive does not contain bin\m2.conf."
    }

    Move-Item -Path $mavenDir.FullName -Destination $targetDir
    Remove-Item -Recurse -Force $tmpDir
    return $targetDir
}

function Select-MailMcpJava {
    if ($env:MAIL_MCP_JAVA -and (Test-MailMcpJavaCompatible $env:MAIL_MCP_JAVA)) {
        $script:MailMcpJava = $env:MAIL_MCP_JAVA
        $script:MailMcpJavaHome = Split-Path (Split-Path $script:MailMcpJava -Parent) -Parent
        return
    }

    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
        if (Test-MailMcpJavaCompatible $candidate) {
            $script:MailMcpJava = $candidate
            $script:MailMcpJavaHome = $env:JAVA_HOME
            return
        }
    }

    $systemJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($systemJava -and (Test-MailMcpJavaCompatible $systemJava.Source)) {
        $script:MailMcpJava = $systemJava.Source
        $script:MailMcpJavaHome = Split-Path (Split-Path $script:MailMcpJava -Parent) -Parent
        return
    }

    $compactJava = Join-Path $script:MailMcpCompactRuntimeDir "bin\java.exe"
    if (Test-MailMcpJavaCompatible $compactJava) {
        Set-MailMcpJavaHome $script:MailMcpCompactRuntimeDir
        return
    }

    $localJava = Join-Path $script:MailMcpJdkDir "bin\java.exe"
    if (-not (Test-MailMcpJavaCompatible $localJava)) {
        Install-MailMcpJdk
    }

    Set-MailMcpJavaHome $script:MailMcpJdkDir
}

function Select-MailMcpBuildJava {
    if ($env:MAIL_MCP_JAVA) {
        $candidateHome = Split-Path (Split-Path $env:MAIL_MCP_JAVA -Parent) -Parent
        if (Test-MailMcpJdkHomeCompatible $candidateHome) {
            $script:MailMcpJava = $env:MAIL_MCP_JAVA
            $script:MailMcpJavaHome = $candidateHome
            return
        }
    }

    if ($env:JAVA_HOME -and (Test-MailMcpJdkHomeCompatible $env:JAVA_HOME)) {
        Set-MailMcpJavaHome $env:JAVA_HOME
        return
    }

    $systemJava = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ($systemJava) {
        $candidateHome = Split-Path (Split-Path $systemJava.Source -Parent) -Parent
        if (Test-MailMcpJdkHomeCompatible $candidateHome) {
            $script:MailMcpJava = $systemJava.Source
            $script:MailMcpJavaHome = $candidateHome
            return
        }
    }

    if (-not (Test-MailMcpJdkHomeCompatible $script:MailMcpJdkDir)) {
        Install-MailMcpJdk
    }

    Set-MailMcpJavaHome $script:MailMcpJdkDir
}

function New-MailMcpCompactRuntime {
    if (-not (Test-MailMcpJdkHomeCompatible $script:MailMcpJdkDir)) {
        throw "Local JDK not found under $script:MailMcpJdkDir."
    }

    $jlink = Join-Path $script:MailMcpJdkDir "bin\jlink.exe"
    $tmpRuntime = Join-Path $script:MailMcpRuntimeDir "tmp\mail-mcp-runtime-$script:MailMcpRequiredJava"
    if (Test-Path $tmpRuntime) {
        Remove-Item -Recurse -Force $tmpRuntime
    }
    if (Test-Path $script:MailMcpCompactRuntimeDir) {
        Remove-Item -Recurse -Force $script:MailMcpCompactRuntimeDir
    }
    New-Item -ItemType Directory -Force -Path (Split-Path $tmpRuntime -Parent) | Out-Null

    $modules = @(
        "java.se",
        "jdk.charsets",
        "jdk.crypto.ec",
        "jdk.naming.dns",
        "jdk.unsupported"
    )

    Write-Host "Creating a compact Java runtime for opcoach-mcp-mail..."
    & $jlink `
        "--add-modules" ($modules -join ",") `
        "--bind-services" `
        "--strip-debug" `
        "--no-header-files" `
        "--no-man-pages" `
        "--compress" "zip-6" `
        "--output" $tmpRuntime
    if ($LASTEXITCODE -ne 0) {
        throw "jlink failed with exit code $LASTEXITCODE."
    }

    Move-Item -Path $tmpRuntime -Destination $script:MailMcpCompactRuntimeDir
    $compactJava = Join-Path $script:MailMcpCompactRuntimeDir "bin\java.exe"
    if (-not (Test-MailMcpJavaCompatible $compactJava)) {
        throw "Compact Java runtime was created but java.exe is not usable."
    }
}

function Remove-MailMcpLocalJdk {
    $compactJava = Join-Path $script:MailMcpCompactRuntimeDir "bin\java.exe"
    if (-not (Test-MailMcpJavaCompatible $compactJava)) {
        throw "Refusing to remove the local JDK because no compact runtime is available."
    }

    if (Test-Path $script:MailMcpJdkDir) {
        Remove-Item -Recurse -Force $script:MailMcpJdkDir
    }

    $downloadDir = Join-Path $script:MailMcpRuntimeDir "downloads"
    if (Test-Path $downloadDir) {
        Get-ChildItem -Path $downloadDir -Filter "temurin-jdk-*.zip" -ErrorAction SilentlyContinue |
            Remove-Item -Force
    }

    Set-MailMcpJavaHome $script:MailMcpCompactRuntimeDir
}

function Optimize-MailMcpLocalJava {
    if (-not (Test-MailMcpJdkHomeCompatible $script:MailMcpJdkDir)) {
        return
    }

    $jdkSize = Get-MailMcpDirectorySizeMb $script:MailMcpJdkDir
    Write-Host
    Write-Host "The local JDK used for the build takes about $jdkSize MB."
    $answer = Read-Host "Replace it with a smaller runtime and remove the full local JDK? [Y/n]"
    if ($answer -and $answer.Trim().ToLowerInvariant().StartsWith("n")) {
        return
    }

    New-MailMcpCompactRuntime
    $runtimeSize = Get-MailMcpDirectorySizeMb $script:MailMcpCompactRuntimeDir
    Remove-MailMcpLocalJdk
    Write-Host "Compact runtime ready: $script:MailMcpCompactRuntimeDir ($runtimeSize MB)."
}

function Invoke-MailMcpWithJava {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [string[]]$Arguments = @()
    )

    Select-MailMcpJava

    $oldJavaHome = $env:JAVA_HOME
    $oldPath = $env:Path
    try {
        $env:JAVA_HOME = $script:MailMcpJavaHome
        $env:Path = (Join-Path $script:MailMcpJavaHome "bin") + ";" + $oldPath
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Command failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:Path = $oldPath
    }
}

function Invoke-MailMcpMaven {
    param([string[]]$Arguments = @())

    Select-MailMcpBuildJava
    $mavenHome = Install-MailMcpMaven
    $classWorlds = Get-ChildItem -Path (Join-Path $mavenHome "boot") -Filter "plexus-classworlds-*.jar" |
        Select-Object -First 1
    if (-not $classWorlds) {
        throw "Maven launcher not found under $mavenHome\boot."
    }

    $oldJavaHome = $env:JAVA_HOME
    $oldPath = $env:Path
    $oldMavenProjectBaseDir = $env:MAVEN_PROJECTBASEDIR
    try {
        $env:JAVA_HOME = $script:MailMcpJavaHome
        $env:Path = (Join-Path $script:MailMcpJavaHome "bin") + ";" + $oldPath
        $env:MAVEN_PROJECTBASEDIR = $script:RepoDir

        $javaArgs = @(
            "-classpath",
            $classWorlds.FullName,
            "-Dclassworlds.conf=$(Join-Path $mavenHome "bin\m2.conf")",
            "-Dmaven.home=$mavenHome",
            "-Dmaven.multiModuleProjectDirectory=$script:RepoDir",
            "-Dlibrary.jansi.path=$(Join-Path $mavenHome "lib\jansi-native")",
            "org.codehaus.plexus.classworlds.launcher.Launcher"
        ) + $Arguments

        & $script:MailMcpJava @javaArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Maven failed with exit code $LASTEXITCODE."
        }
    } finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:Path = $oldPath
        $env:MAVEN_PROJECTBASEDIR = $oldMavenProjectBaseDir
    }
}

function Join-MailMcpProcessArguments {
    param([string[]]$Arguments = @())

    $quoted = foreach ($argument in $Arguments) {
        if ($null -eq $argument) {
            '""'
        } else {
            '"' + ($argument.Replace('"', '\"')) + '"'
        }
    }
    return ($quoted -join " ")
}
