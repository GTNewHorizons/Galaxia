param(
    [ValidateSet('unit', 'api', 'gui', 'showcase', 'all')]
    [string] $Suite = 'unit',
    [string] $HorizonQaRoot = '',
    [string] $HorizonQaJar = '',
    [string] $ReportDir = '',
    [int] $TimeoutSeconds = 480,
    [string[]] $GradleArguments = @('-PusesMixinDebug=false')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

if ($Suite -in @('unit', 'all')) {
    Push-Location $projectRoot
    try {
        & (Join-Path $projectRoot 'gradlew.bat') test build --console=plain --max-workers=2 @GradleArguments
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $testCount = 0
        Get-ChildItem (Join-Path $projectRoot 'build/test-results/test/TEST-*.xml') -ErrorAction SilentlyContinue |
            ForEach-Object {
                [xml] $result = Get-Content -LiteralPath $_.FullName -Raw
                $testCount += [int] $result.testsuite.tests
            }
        if ($testCount -eq 0) { throw 'Gradle produced no JUnit results. Check test discovery before accepting this run' }
    } finally {
        Pop-Location
    }
    if ($Suite -eq 'unit') { exit 0 }
}

if (-not $HorizonQaRoot) {
    $commonDir = (& git -C $projectRoot rev-parse --path-format=absolute --git-common-dir).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the main repository for Horizon-QA lookup' }
    $githubRoot = Split-Path (Split-Path $commonDir -Parent) -Parent
    $HorizonQaRoot = Join-Path $githubRoot 'Horizon-QA'
}
if (-not $HorizonQaJar) { $HorizonQaJar = Join-Path $projectRoot 'AI/Cache/horizonqa/horizonqa-client-prototype-dev.jar' }
if (-not $ReportDir) {
    $ReportDir = Join-Path $projectRoot ('build/horizonqa/' + $Suite + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
}
$launcher = Join-Path $HorizonQaRoot 'scripts/run-client-tests.ps1'
if (-not (Test-Path -LiteralPath $launcher)) { throw "Horizon-QA launcher not found: $launcher. Set -HorizonQaRoot" }
if (-not (Test-Path -LiteralPath $HorizonQaJar)) { throw "Client testing JAR not found: $HorizonQaJar. Set -HorizonQaJar" }

$selectors = switch ($Suite) {
    'api' { 'galaxia:FacilityGameplayGameTests.' }
    'gui' { 'galaxia:StationGuiGameTests.,galaxia:StarmapGameTests.,galaxia:ModuleConstructionGuiGameTests.,galaxia:SatelliteNetworkGuiGameTests.' }
    'showcase' { 'galaxia:SatelliteNetworkGuiGameTests.' }
    'all' { 'galaxia' }
}

& $launcher -ProjectRoot $projectRoot -Task runClient -Tests $selectors -HorizonQaJar $HorizonQaJar `
    -ReportDir $ReportDir -TimeoutSeconds $TimeoutSeconds -GradleArguments $GradleArguments
exit $LASTEXITCODE
