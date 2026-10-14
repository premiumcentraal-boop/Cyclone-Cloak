# Read-only device collection. No root, install, log clearing, restart or network.
[CmdletBinding()]
param(
    [string]$Adb = 'C:/Users/Agent/android-sdk/platform-tools/adb.exe',
    [string]$Serial,
    [string]$InputLog,
    [string]$OutputDirectory = '.',
    [string]$Subsystem = '',
    [ValidateSet('', 'INFO', 'WARNING', 'ERROR')][string]$Severity = '',
    [string]$ActionId = '',
    [string]$Text = ''
)
$ErrorActionPreference = 'Stop'
function Safe-Label([string]$Value) {
    if ($Value -match '^[a-zA-Z][a-zA-Z0-9_.-]{0,63}$') { return $Value }
    return 'redacted'
}
function Safe-Code([string]$Value) {
    if ($Value -match '^[A-Z][A-Z0-9_]{0,63}$') { return $Value }
    return 'UNSPECIFIED'
}
if ($InputLog) {
    if ((Get-Item -LiteralPath $InputLog).Length -gt 2097152) { throw 'Input log exceeds 2 MiB cap' }
    $lines = Get-Content -LiteralPath $InputLog
} else {
    if (-not $Serial) { throw 'Select a device explicitly with -Serial, or use -InputLog' }
    $lines = & $Adb -s $Serial logcat -d -t 2000 -v threadtime 'CloakDiagnostics:I' 'CloakModule:I' 'CloakProbeDiagnostics:I' '*:S'
    if ($LASTEXITCODE -ne 0) { throw 'Could not read device logs' }
}
$events = @()
$ignored = 0
foreach ($line in ($lines | Select-Object -Last 2000)) {
    $event = $null
    try {
        if ($line -match 'CloakDiagnostics\s*:\s*(\{.*\})$') {
            $j = $Matches[1] | ConvertFrom-Json
            if ($j.severity -notin @('INFO','WARNING','ERROR') -or $j.transition -notin @('STARTED','COMPLETED','DEGRADED','FAILED','CRASH')) { throw 'Invalid diagnostic state' }
            $target = $null
            if ($j.target_id -cmatch '^([a-fA-F0-9-]{16,64}|[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+)$') { $target = $j.target_id }
            $event = [ordered]@{
                timestamp = [long]$j.timestamp; severity = $j.severity; source = 'app'
                subsystem = Safe-Label $j.subsystem; action_id = Safe-Label $j.action_id
                thread = ([string]$j.thread -replace '[^0-9]', '').Substring(0, [Math]::Min(20, ([string]$j.thread -replace '[^0-9]', '').Length))
                process = ([string]$j.process -replace '[^0-9]', '').Substring(0, [Math]::Min(20, ([string]$j.process -replace '[^0-9]', '').Length))
                target_id = $target; transition = $j.transition
                message = 'Structured local action'; reason = Safe-Code $j.reason
                error_summary = if ($j.error_summary) { Safe-Label $j.error_summary } else { $null }
                duration_ms = if ($null -ne $j.duration_ms) { [Math]::Max(0, [long]$j.duration_ms) } else { $null }
            }
        } elseif ($line -match 'CloakProbeDiagnostics\s*:\s*(\{.*\})$') {
            $j = $Matches[1] | ConvertFrom-Json
            if ($j.status -notin @('VERIFIED','PASS','FAIL','BLOCKED','OBSERVED','UNMAPPED','INCOMPLETE','PARTIAL')) { throw 'Invalid probe state' }
            $event = [ordered]@{ timestamp = if ($j.timestamp) { [long]$j.timestamp } else { [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() }; severity = if ($j.status -eq 'FAIL') { 'ERROR' } elseif ($j.status -in @('PASS','VERIFIED')) { 'INFO' } else { 'WARNING' }
                source = 'probe'; subsystem = 'probe'; action_id = Safe-Label $j.field; reason = $j.status; message = 'Probe row completed'
                process = ([string]$j.process -replace '[^0-9]', ''); thread = ([string]$j.thread -replace '[^0-9]', '')
                target_id = $null; error_summary = $null; duration_ms = $null
                transition = if ($j.status -eq 'FAIL') { 'FAILED' } elseif ($j.status -in @('PASS','VERIFIED')) { 'COMPLETED' } else { 'DEGRADED' }
                metadata_status = if ($j.timestamp) { 'PROBE_EVENT' } else { 'COLLECTION_TIME_FALLBACK' } }
        } elseif ($line -match 'CloakModule\s*:') {
            # Legacy native log text may contain paths/identifiers; retain only categories.
            $reason = if ($line -match 'index unreadable') { 'INDEX_UNREADABLE' } elseif ($line -match 'profile unreadable') { 'PROFILE_UNREADABLE' }
                elseif ($line -match 'root companion connection failed') { 'COMPANION_CONNECTION_FAILED' }
                elseif ($line -match 'property callback hook failed') { 'PROPERTY_CALLBACK_HOOK_FAILED' }
                elseif ($line -match 'failed|unreadable|missing|cannot|error') { 'RUNTIME_ATTENTION' } elseif ($line -match 'installed') { 'HOOK_INSTALLED' } else { 'RUNTIME_OBSERVED' }
            $warn = $reason -notin @('HOOK_INSTALLED', 'RUNTIME_OBSERVED')
            $event = [ordered]@{ timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds(); severity = if ($warn) { 'WARNING' } else { 'INFO' }
                source = 'module'; subsystem = 'runtime'; action_id = 'legacy-native-log'; reason = $reason; message = 'Native module event'
                process = 'unknown'; thread = 'unknown'; target_id = $null; error_summary = $null; duration_ms = $null
                transition = if ($warn) { 'DEGRADED' } else { 'COMPLETED' }
                metadata_status = 'COLLECTION_TIME_LEGACY_METADATA_UNAVAILABLE' }
        }
    } catch { $ignored++; continue }
    if ($null -eq $event) { $ignored++; continue }
    $encoded = $event | ConvertTo-Json -Depth 8 -Compress
    if ($event.subsystem -notlike "*$Subsystem*" -or ($Severity -and $event.severity -ne $Severity) -or
        $event.action_id -notlike "*$ActionId*" -or $encoded -notlike "*$Text*") { continue }
    $events += $event
}
$report = [ordered]@{ schema_version = 1; redacted = $true; created_at = [DateTimeOffset]::UtcNow.ToString('o')
    source = 'read-only-logcat'; events = @($events); ignored_lines = $ignored
    limitations = 'No profile bodies, device serial, log payloads or attestation. Legacy module metadata unavailable. Versions/counters require in-app bundle.' }
$json = $report | ConvertTo-Json -Depth 12
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$path = Join-Path (Resolve-Path -LiteralPath $OutputDirectory) ('cloak-diagnostics-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.zip')
Add-Type -AssemblyName System.IO.Compression
$stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew)
try {
    $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        $entry = $zip.CreateEntry('diagnostics.json')
        $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
        try { $writer.Write($json) } finally { $writer.Dispose() }
    } finally { $zip.Dispose() }
} finally { $stream.Dispose() }
Write-Output $path
