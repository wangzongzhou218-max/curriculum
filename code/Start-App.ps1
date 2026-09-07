param([ValidateSet('backend', 'frontend')][string]$Service = 'backend')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if ($Service -eq 'frontend') {
    . "$projectRoot/scripts/Enter-DevEnvironment.ps1"
    pnpm.cmd --dir "$PSScriptRoot/frontend" dev
} else {
    & "$projectRoot/scripts/Start-DevDatabase.ps1"
    foreach ($configLine in Get-Content -LiteralPath "$projectRoot/.env.local") {
        if ($configLine -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
    mvn.cmd -f "$PSScriptRoot/backend/pom.xml" spring-boot:run
}
if ($LASTEXITCODE -ne 0) { throw "$Service exited with code $LASTEXITCODE" }
