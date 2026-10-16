[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z][a-z0-9-]{0,63}$')][string]$Scenario,
    [ValidateSet('all','environment','bind','publish','launch','probes','switch','post-switch-probes','diff','crash-log','summary')][string]$Phase = 'all',
    [string]$Adb = 'C:/Users/Agent/android-sdk/platform-tools/adb.exe',
    [string]$Serial,
    [int]$User = -1,
    [int]$TargetUser = -1,
    [switch]$FullProfile,
    [switch]$Cleanup,
    [string]$Apk,
    [string]$OutputDirectory,
    [ValidateRange(10,300)][int]$TimeoutSeconds = 90
)
$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'profile_check.py'
$arguments = @('run','--','python',$runner,'--scenario',$Scenario,'--phase',$Phase,'--adb',$Adb,'--timeout',[string]$TimeoutSeconds)
if ($Serial) { $arguments += @('--serial',$Serial) }
if ($User -ge 0) { $arguments += @('--user',[string]$User) }
if ($TargetUser -ge 0) { $arguments += @('--target-user',[string]$TargetUser) }
if ($Cleanup) { $arguments += '--cleanup' }
if ($FullProfile) { $arguments += '--full-profile' }
if ($Apk) { $arguments += @('--apk',$Apk) }
if ($OutputDirectory) { $arguments += @('--output',$OutputDirectory) }
& uv @arguments
exit $LASTEXITCODE
