param(
    [Parameter(Mandatory = $true)][string]$Instance,
    [string]$Gradle = '.\gradlew.bat',
    [string]$AssetIndex = '34',
    [ValidateSet('opengl', 'vulkan')][string]$Backend = 'opengl'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$instancePath = (Resolve-Path -LiteralPath $Instance).Path
$assetPath = Join-Path (Split-Path (Split-Path $instancePath -Parent) -Parent) 'assets'
$modPath = Join-Path $projectRoot 'build/compat-mods'
New-Item -ItemType Directory -Path $modPath -Force | Out-Null
# Only this script's disposable copies are cleared; the original instance is read-only.
Get-ChildItem -LiteralPath $modPath -Filter '*.jar' | Remove-Item
$index = 0
Get-ChildItem -LiteralPath (Join-Path $instancePath 'mods') -Filter '*.jar' |
    Where-Object { $_.Name -notlike 'convert-table-*' -and $_.Name -notlike 'fabric-api-*' } |
    ForEach-Object {
        $index++
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $modPath ('compat-{0:D2}.jar' -f $index))
    }
Push-Location $projectRoot
try {
    & $Gradle "-Dorg.gradle.native.dir=$projectRoot/build/gradle-native" `
        "-PcompatInstance=$instancePath" "-PcompatMods=$modPath" `
        "-PtestAssets=$assetPath" "-PtestAssetIndex=$AssetIndex" "-PtestBackend=$Backend" `
        runClientGameTest -x downloadAssets
    if ($LASTEXITCODE -ne 0) { throw "Compatibility test failed: $LASTEXITCODE" }
} finally { Pop-Location }
