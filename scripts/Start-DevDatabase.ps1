$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Enter-DevEnvironment.ps1"
& "$PSScriptRoot\Initialize-LocalConfig.ps1"
$projectRoot = Split-Path $PSScriptRoot -Parent
function Test-DockerEngine {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    $desktopStatus = docker desktop status --format json 2>$null | ConvertFrom-Json
    if ($null -eq $desktopStatus -or $desktopStatus.Status -ne 'running') {
        $ErrorActionPreference = $previousPreference
        return $false
    }
    docker info --format '{{.OSType}}' 2>$null | Out-Null
    $ready = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previousPreference
    return $ready
}
if (-not (Test-DockerEngine)) {
    $dockerDesktop = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $dockerDesktop)) { throw 'Docker Desktop is not installed.' }
    Write-Host 'Starting Docker Desktop and waiting for its engine...'
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
    $dockerReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        Start-Sleep -Seconds 2
        if (Test-DockerEngine) { $dockerReady = $true; break }
    }
    if (-not $dockerReady) { throw 'Docker Desktop did not become ready. Restart Windows if virtual machine support was just enabled, then retry.' }
}
docker compose --project-directory $projectRoot --env-file "$projectRoot\.env.local" -f "$projectRoot\compose.yaml" up -d --wait mysql
if ($LASTEXITCODE -ne 0) {
    if (-not (Test-DockerEngine)) {
        throw 'Docker Linux engine is unavailable. Enable Windows Subsystem for Linux and Virtual Machine Platform as administrator, then restart Windows.'
    }
    throw 'Development MySQL did not become healthy. Review the Compose output above.'
}
docker compose --project-directory $projectRoot --env-file "$projectRoot\.env.local" -f "$projectRoot\compose.yaml" exec -T mysql mysql -u curriculum_migrator -D curriculum -e "SELECT VERSION(), @@port, @@character_set_server, @@collation_server, @@transaction_isolation"
if ($LASTEXITCODE -ne 0) { throw 'Database server verification failed.' }
