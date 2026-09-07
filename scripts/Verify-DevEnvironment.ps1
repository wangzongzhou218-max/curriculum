param([switch]$RequireDocker)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\Enter-DevEnvironment.ps1"
foreach ($command in @('java', 'javac', 'mvn.cmd', 'node', 'pnpm.cmd')) {
    & $command --version
    if ($LASTEXITCODE -ne 0) { throw "$command version check failed" }
}
$projectRoot = Split-Path $PSScriptRoot -Parent
$checkDir = Join-Path $projectRoot '.tools\java-check'
New-Item -ItemType Directory -Force $checkDir | Out-Null
@'
public class Java21Check {
    public static void main(String[] args) {
        if (Runtime.version().feature() != 21) throw new AssertionError("JDK 21 required");
        System.out.println("JAVA21_COMPILE_RUN_OK " + Runtime.version());
    }
}
'@ | Set-Content (Join-Path $checkDir 'Java21Check.java') -Encoding ascii
& javac --release 21 -d $checkDir (Join-Path $checkDir 'Java21Check.java')
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
& java -cp $checkDir Java21Check
if ($LASTEXITCODE -ne 0) { throw 'Java execution failed' }
if ($RequireDocker) {
    docker version
    if ($LASTEXITCODE -ne 0) { throw 'Docker server is unavailable' }
    $osType = docker info --format '{{.OSType}}'
    if ($LASTEXITCODE -ne 0 -or $osType -ne 'linux') { throw 'Linux Docker engine required' }
    docker compose version
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is unavailable' }
}
