# Dot-source from PowerShell: . .\scripts\Enter-DevEnvironment.ps1
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk-21.0.12.1+1'
$env:MAVEN_HOME = Join-Path $projectRoot '.tools\apache-maven-3.9.16'
$profileRoot = Split-Path (Split-Path $env:LOCALAPPDATA -Parent) -Parent
$mavenRepository = Join-Path $profileRoot '.m2\repository'
$env:MAVEN_OPTS = "-Dmaven.repo.local=`"$mavenRepository`""
$toolPaths = @(
    (Join-Path $env:JAVA_HOME 'bin'),
    (Join-Path $env:MAVEN_HOME 'bin'),
    (Join-Path $projectRoot '.tools\node-v24.20.0-win-x64'),
    (Join-Path $projectRoot '.tools\pnpm'),
    (Join-Path $projectRoot '.tools\k6-v2.2.0-windows-amd64')
)
foreach ($toolPath in $toolPaths) {
    if (-not (Test-Path -LiteralPath $toolPath)) { throw "Missing tool directory: $toolPath" }
}
$env:Path = (($toolPaths + ($env:Path -split ';') | Select-Object -Unique) -join ';')
$dockerBin = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin'
if (Test-Path -LiteralPath $dockerBin) { $env:Path = "$dockerBin;$env:Path" }
Write-Host 'Curriculum development environment activated (Java 21 / Maven 3.9.16 / Node 24.20.0 / pnpm 11.19.0).'
