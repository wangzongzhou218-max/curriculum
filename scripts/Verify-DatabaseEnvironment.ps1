$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Enter-DevEnvironment.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\Verify-DevEnvironment.ps1" -RequireDocker
& "$PSScriptRoot\Start-DevDatabase.ps1"
mvn.cmd -B -f "$projectRoot\tools\environment-check\backend\pom.xml" -Pdatabase-check verify
if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL environment verification failed; no test is silently skipped.' }
