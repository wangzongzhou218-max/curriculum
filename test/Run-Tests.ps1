param([switch]$Integration, [switch]$Browser)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
. "$projectRoot/scripts/Enter-DevEnvironment.ps1"
mvn.cmd -f "$projectRoot/code/backend/pom.xml" test
if ($LASTEXITCODE -ne 0) { throw 'Backend unit tests failed.' }
pnpm.cmd --dir "$projectRoot/code/frontend" typecheck
if ($LASTEXITCODE -ne 0) { throw 'Frontend typecheck failed.' }
pnpm.cmd --dir $PSScriptRoot unit
if ($LASTEXITCODE -ne 0) { throw 'Frontend unit tests failed.' }
if ($Browser) {
    pnpm.cmd --dir $PSScriptRoot browser
    if ($LASTEXITCODE -ne 0) { throw 'Chrome interface tests failed.' }
}
if ($Integration) {
    mvn.cmd -f "$projectRoot/code/backend/pom.xml" verify
    if ($LASTEXITCODE -ne 0) { throw 'Real MySQL integration tests failed; inspect Docker and failsafe reports.' }
}
