param(
    [string]$Source,
    [string]$Version,
    [string]$ExpectedHash,
    [string]$ExpectedInstalledName,
    [string]$ExpectedInstalledHash,
    [switch]$Wait,
    [switch]$SelfTest
)

# By FastedCorsi. With -Wait, one hidden local process waits for Minecraft and exits after installation.
# Standalone external installer: never shipped inside the public mod JAR.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Test-GameProcess([string]$Name, [string]$Arguments) {
    if ($Name -notmatch '(?i)^java(w)?\.exe$') { return $false }
    if ([string]::IsNullOrWhiteSpace($Arguments)) { return $true }
    if ($Arguments -match 'org\.gradle\.(launcher\.daemon\.bootstrap\.GradleDaemon|process\.internal\.worker\.GradleWorkerMain)') { return $false }
    if ($Arguments -match 'KnotClient|net\.minecraft\.client\.main\.Main|net\.minecraft\.launchwrapper\.Launch|cpw\.mods\.(modlauncher|bootstraplauncher)|--gameDir') { return $true }
    if ($Arguments -match 'runtime[\\/]launcher\.jar') { return $false }
    return $Arguments -match '(?i)\.tropimon'
}

function Test-GameClosed {
    return @(Get-CimInstance Win32_Process | Where-Object { Test-GameProcess $_.Name $_.CommandLine }).Count -eq 0
}

function Test-TargetName([string]$Name) {
    return $Name -match '^TropimonChatFilter-[0-9A-Za-z.+_-]+\.jar$'
}

if ($SelfTest) {
    $cases = @(
        @('java.exe', 'java -jar C:\fixture\runtime\launcher.jar', $false),
        @('javaw.exe', 'java net.fabricmc.loader.impl.launch.knot.KnotClient --gameDir C:\fixture', $true),
        @('java.exe', 'java net.minecraft.client.main.Main', $true),
        @('java.exe', '', $true),
        @('java.exe', 'java org.gradle.launcher.daemon.bootstrap.GradleDaemon C:\fixture\.tropimon', $false),
        @('java.exe', 'java org.gradle.process.internal.worker.GradleWorkerMain', $false),
        @('Tropimon.exe', '', $false),
        @('powershell.exe', '-File InstallWhenClosed.ps1', $false)
    )
    foreach ($case in $cases) {
        if ((Test-GameProcess $case[0] $case[1]) -ne $case[2]) { throw 'Process classification test failed.' }
    }
    foreach ($invalid in @('..\other.jar', 'other-mod.jar', 'TropimonChatFilter-a/b.jar')) {
        if (Test-TargetName $invalid) { throw 'Target validation test failed.' }
    }
    if (-not (Test-TargetName 'TropimonChatFilter-0.0.1+1.21.1-LOCAL.jar')) { throw 'Expected target name rejected.' }
    Write-Output 'Installer self-tests OK: 12 synthetic cases; no instance accessed.'
    return
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
function Assert-PlainPath([string]$Path) {
    if (((Get-Item -LiteralPath $Path).Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Redirected path.' }
}

function Read-Mod([string]$Path) {
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $entry = $zip.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { return $null }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { return $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
}

function Find-Installed([string]$Mods) {
    foreach ($jar in Get-ChildItem -LiteralPath $Mods -Filter '*.jar' -File) {
        Assert-PlainPath $jar.FullName
        $metadata = Read-Mod $jar.FullName
        if ($null -ne $metadata -and $metadata.id -eq 'tropimon_chat_filter') { $jar }
    }
}

function Assert-Release([string]$Path) {
    Assert-PlainPath $Path
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $ExpectedHash) { throw 'Release integrity mismatch.' }
    $mod = Read-Mod $Path
    if ($null -eq $mod -or $mod.id -ne 'tropimon_chat_filter' -or $mod.version -ne $Version -or
        @($mod.authors).Count -ne 1 -or $mod.authors[0] -cne 'By FastedCorsi') { throw 'Unexpected release metadata.' }
}

$mutex = $null
$owned = $false
$pending = $null
try {
    if ($Version -notmatch '^[0-9A-Za-z.+_-]+$' -or $ExpectedHash -notmatch '^[A-Fa-f0-9]{64}$' -or
        $ExpectedInstalledHash -notmatch '^[A-Fa-f0-9]{64}$' -or -not (Test-TargetName $ExpectedInstalledName)) { throw 'Explicit release and installed identity required.' }
    $mutex = [Threading.Mutex]::new($false, 'Local\TropimonChatFilterDeferredInstall')
    try { $owned = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $owned = $true }
    if (-not $owned) { Write-Output 'WAITING: another installer is active.'; return }
    $sourcePath = (Resolve-Path -LiteralPath $Source).Path
    Assert-Release $sourcePath
    while (-not (Test-GameClosed)) {
        if (-not $Wait) {
            Write-Output 'WAITING: game running or Java process unidentifiable. No instance modified.'
            return
        }
        Start-Sleep -Seconds 5
    }

    $instance = (Resolve-Path -LiteralPath (Join-Path $env:APPDATA '.Tropimon')).Path
    $mods = (Resolve-Path -LiteralPath (Join-Path $instance 'mods')).Path
    Assert-PlainPath $instance
    Assert-PlainPath $mods
    if ($sourcePath.StartsWith($mods + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Source must stay outside loaded mods.' }
    $target = Join-Path $mods "TropimonChatFilter-$Version+1.21.1-LOCAL.jar"
    $installed = @(Find-Installed $mods)
    if ($installed.Count -ne 1) { throw 'Exactly one known installed Chat Filter is required.' }
    if ($installed[0].FullName -eq $target -and (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -eq $ExpectedHash) {
        Assert-Release $target
        Write-Output 'INSTALLED: already current; one verified Chat Filter JAR.'
        return
    }
    $old = $installed[0].FullName
    if ($installed[0].Name -ne $ExpectedInstalledName -or
        (Get-FileHash -LiteralPath $old -Algorithm SHA256).Hash -ne $ExpectedInstalledHash) { throw 'Installed release changed since preparation.' }
    if ($target -ne $old -and (Test-Path -LiteralPath $target)) { throw 'Target already occupied.' }
    $probe = [IO.File]::Open($old, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    $probe.Dispose()

    $archive = Join-Path $instance 'mod-archive'
    [void](New-Item -ItemType Directory -Path $archive -Force)
    Assert-PlainPath $archive
    $backupRoot = Join-Path $archive 'tropimon-chat-filter'
    [void](New-Item -ItemType Directory -Path $backupRoot -Force)
    Assert-PlainPath $backupRoot
    $backup = Join-Path $backupRoot ($ExpectedInstalledName + '.' + [Guid]::NewGuid().ToString('N') + '.bak')
    $pending = Join-Path $mods ('.chat-filter-' + [Guid]::NewGuid().ToString('N') + '.pending')
    [IO.File]::Copy($sourcePath, $pending, $false)
    Assert-Release $pending
    if (-not (Test-GameClosed)) { Write-Output 'WAITING: game started before replacement.'; return }
    if ((Get-FileHash -LiteralPath $old -Algorithm SHA256).Hash -ne $ExpectedInstalledHash) { throw 'Installed JAR changed before replacement.' }
    $installedAgain = @(Find-Installed $mods)
    if ($installedAgain.Count -ne 1 -or $installedAgain[0].FullName -ne $old) { throw 'Loaded mods changed before replacement.' }
    # Explicit resolved files only; move the old JAR out before activating the new one.
    [IO.File]::Move($old, $backup)
    try { [IO.File]::Move($pending, $target); $pending = $null }
    catch { [IO.File]::Move($backup, $old); throw }
    Assert-Release $target
    if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $ExpectedInstalledHash) { throw 'Backup verification failed.' }
    $final = @(Find-Installed $mods)
    if ($final.Count -ne 1 -or $final[0].FullName -ne $target) { throw 'Final installed inventory mismatch.' }
    Write-Output 'INSTALLED: copy, backup and single Chat Filter JAR verified.'
} catch {
    # Do not expose command-line arguments, private paths or exception payloads.
    $failure = $_.Exception
    while ($null -ne $failure.InnerException) { $failure = $failure.InnerException }
    if (($failure.HResult -band 0xFFFF) -in @(32, 33)) {
        Write-Output 'WAITING: file locked; retry after the game closes.'
    } else {
        Write-Output 'BLOCKED: source, target, access or integrity needs review; no forced replacement.'
        exit 2
    }
} finally {
    if ($null -ne $pending -and [IO.File]::Exists($pending)) {
        try { [IO.File]::Delete($pending) }
        catch { Write-Output 'BLOCKED: inactive staging file retained for review.' }
    }
    if ($owned) { $mutex.ReleaseMutex() }
    if ($null -ne $mutex) { $mutex.Dispose() }
}
