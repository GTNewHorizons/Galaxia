param(
    [ValidateSet('unit', 'physical-stations', 'automated-facilities', 'rocket-production', 'starmap', 'satellites', 'all')]
    [string[]] $Suite = @('unit'),
    [string] $HorizonQaRoot = '',
    [string] $HorizonQaJar = '',
    [string] $ReportDir = '',
    [int] $TimeoutSeconds = 480,
    [string[]] $GradleArguments = @('-PusesMixinDebug=false'),
    [switch] $ListSuites,
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

# Keep gameplay and GUI scenarios for each domain together.
$groups = [ordered]@{
    'physical-stations' = @('galaxia:AirlockGuiGameTests.')
    'automated-facilities' = @('galaxia:FacilityGameplayGameTests.', 'galaxia:StationGuiGameTests.')
    'rocket-production' = @('galaxia:ModuleConstructionGuiGameTests.')
    'starmap' = @('galaxia:StarmapGameTests.')
    'satellites' = @('galaxia:SatelliteNetworkGuiGameTests.')
}
if ($ListSuites) {
    'unit: JUnit tests and build'
    foreach ($entry in $groups.GetEnumerator()) { "{0}: {1}" -f $entry.Key, ($entry.Value -join ',') }
    'all: JUnit tests, build and every Galaxia Horizon QA scenario (before opening a PR)'
    exit 0
}
if ($Suite -contains 'all' -and $Suite.Count -ne 1) { throw 'Select all on its own' }
$runUnit = $Suite -contains 'unit' -or $Suite -contains 'all'
$selectors = if ($Suite -contains 'all') {
    'galaxia'
} else {
    (@($Suite | ForEach-Object { if ($groups.Contains($_)) { $groups[$_] } }) | Select-Object -Unique) -join ','
}
if ($DryRun) {
    [pscustomobject]@{ UnitTests = $runUnit; HorizonQaSelectors = $selectors }
    exit 0
}

if ($runUnit) {
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
}
if (-not $selectors) { exit 0 }

if (-not $HorizonQaRoot) {
    $commonDir = (& git -C $projectRoot rev-parse --path-format=absolute --git-common-dir).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the main repository for Horizon-QA lookup' }
    $githubRoot = Split-Path (Split-Path $commonDir -Parent) -Parent
    $HorizonQaRoot = Join-Path $githubRoot 'Horizon-QA'
}
if (-not $HorizonQaJar) { $HorizonQaJar = Join-Path $projectRoot 'AI/Cache/horizonqa/horizonqa-client-prototype-dev.jar' }
if (-not $ReportDir) {
    $ReportDir = Join-Path $projectRoot ('build/horizonqa/' + ($Suite -join '-') + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
}
$launcher = Join-Path $HorizonQaRoot 'scripts/client-tests'
if (-not (Test-Path -LiteralPath (Join-Path $launcher 'build.gradle'))) { throw "Horizon-QA launcher not found: $launcher. Set -HorizonQaRoot" }
if (-not (Test-Path -LiteralPath $HorizonQaJar)) { throw "Client testing JAR not found: $HorizonQaJar. Set -HorizonQaJar" }

$launcherArguments = @(
    '-p', $launcher, 'runClientTests',
    '--project-root', $projectRoot, '--client-task', ':runClient',
    '--tests', $selectors, '--horizon-qa-jar', $HorizonQaJar,
    '--report-dir', $ReportDir, '--timeout-seconds', $TimeoutSeconds
)
foreach ($argument in $GradleArguments) { $launcherArguments += "--gradle-argument=$argument" }
& (Join-Path $HorizonQaRoot 'gradlew.bat') @launcherArguments
exit $LASTEXITCODE
