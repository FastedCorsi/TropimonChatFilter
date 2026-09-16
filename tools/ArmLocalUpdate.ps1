param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$ExpectedHash,
    [Parameter(Mandatory = $true)][string]$ExpectedInstalledName,
    [Parameter(Mandatory = $true)][string]$ExpectedInstalledHash
)

# By FastedCorsi. External local helper; never embedded in the public JAR.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($Version -notmatch '^[0-9A-Za-z.+_-]+$' -or
    $ExpectedHash -notmatch '^[A-Fa-f0-9]{64}$' -or
    $ExpectedInstalledHash -notmatch '^[A-Fa-f0-9]{64}$' -or
    $ExpectedInstalledName -notmatch '^TropimonChatFilter-[0-9A-Za-z.+_-]+\.jar$') {
    throw 'Explicit release and installed identity required.'
}

$sourceFile = (Resolve-Path -LiteralPath $Source).Path
$installer = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'InstallWhenClosed.ps1')).Path
if ((Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash -ne $ExpectedHash) {
    throw 'Release integrity mismatch.'
}

function Quote-PowerShell([string]$Value) {
    return "'" + $Value.Replace("'", "''") + "'"
}

$command = '& ' + (Quote-PowerShell $installer) +
    ' -Source ' + (Quote-PowerShell $sourceFile) +
    ' -Version ' + (Quote-PowerShell $Version) +
    ' -ExpectedHash ' + (Quote-PowerShell $ExpectedHash) +
    ' -ExpectedInstalledName ' + (Quote-PowerShell $ExpectedInstalledName) +
    ' -ExpectedInstalledHash ' + (Quote-PowerShell $ExpectedInstalledHash) +
    ' -Wait'
$encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @(
    '-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', $encoded
) | Out-Null

Write-Output 'ARMED: waiting for Minecraft to close; the launcher may remain open.'
