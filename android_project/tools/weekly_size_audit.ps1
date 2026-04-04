$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$reportDir = Join-Path $projectRoot "dist"
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir | Out-Null
}

function Get-DirSizeMB([string]$path) {
    if (-not (Test-Path $path)) { return 0 }
    $bytes = (Get-ChildItem -Recurse -File -Force $path | Measure-Object -Property Length -Sum).Sum
    return [math]::Round(($bytes / 1MB), 2)
}

$apkPaths = @(
    (Join-Path $projectRoot "app\build\outputs\apk\demo\debug"),
    (Join-Path $projectRoot "app\build\outputs\apk\contest\debug"),
    (Join-Path $projectRoot "app\build\outputs\apk\contest\release")
)

$apkRows = foreach ($apkDir in $apkPaths) {
    if (Test-Path $apkDir) {
        Get-ChildItem $apkDir -Filter *.apk -File | ForEach-Object {
            [PSCustomObject]@{
                Type = "APK"
                Name = $_.Name
                SizeMB = [math]::Round(($_.Length / 1MB), 2)
                Path = $_.FullName
            }
        }
    }
}

$cacheRows = @(
    [PSCustomObject]@{
        Type = "Cache"
        Name = ".gradle"
        SizeMB = Get-DirSizeMB (Join-Path $projectRoot ".gradle")
        Path = Join-Path $projectRoot ".gradle"
    },
    [PSCustomObject]@{
        Type = "Cache"
        Name = "app/build"
        SizeMB = Get-DirSizeMB (Join-Path $projectRoot "app\build")
        Path = Join-Path $projectRoot "app\build"
    }
)

$logRows = @(
    [PSCustomObject]@{
        Type = "Log"
        Name = "session exports"
        SizeMB = Get-DirSizeMB (Join-Path $env:LOCALAPPDATA "Android\data\com.photosentinel.health\files\exports")
        Path = Join-Path $env:LOCALAPPDATA "Android\data\com.photosentinel.health\files\exports"
    }
)

$reportRows = @()
$reportRows += $apkRows
$reportRows += $cacheRows
$reportRows += $logRows

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$reportPath = Join-Path $reportDir "size_audit_$timestamp.csv"
$reportRows | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $reportPath

Write-Output "size_audit_report=$reportPath"
$reportRows | Sort-Object Type, Name | Format-Table -AutoSize
