$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$configPath = Join-Path $projectRoot '.env.local'
if (Test-Path -LiteralPath $configPath) {
    Write-Host '.env.local exists; credentials preserved.'
    return
}
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
$dbRootPassword = New-LocalSecret
$dbMigrationPassword = New-LocalSecret
$dbAppPassword = New-LocalSecret
$values = [ordered]@{
    MYSQL_ROOT_PASSWORD = $dbRootPassword
    MYSQL_MIGRATION_PASSWORD = $dbMigrationPassword
    DB_URL = 'jdbc:mysql://127.0.0.1:3307/curriculum?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    DB_USERNAME = 'curriculum_app'
    DB_PASSWORD = $dbAppPassword
    FLYWAY_USER = 'curriculum_migrator'
    FLYWAY_PASSWORD = $dbMigrationPassword
    SESSION_SIGNING_KEY = New-LocalSecret
    CONTEXT_SIGNING_KEY = New-LocalSecret
    CSRF_SIGNING_KEY = New-LocalSecret
    OPERATION_HMAC_KEY = New-LocalSecret
}
$initDir = Join-Path $projectRoot '.tools\mysql-init'
New-Item -ItemType Directory -Force $initDir | Out-Null
$values.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" } | Set-Content -LiteralPath $configPath -Encoding ascii
@"
CREATE USER IF NOT EXISTS 'curriculum_app'@'%' IDENTIFIED BY '$dbAppPassword';
GRANT SELECT, INSERT, UPDATE, DELETE ON curriculum.* TO 'curriculum_app'@'%';
"@ | Set-Content -LiteralPath (Join-Path $initDir '01-application-user.sql') -Encoding ascii
# Secrets stay local and are excluded by .gitignore. Restrict their Windows ACLs.
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
foreach ($secretPath in @($configPath, (Join-Path $initDir '01-application-user.sql'))) {
    & icacls.exe $secretPath /inheritance:r /grant:r "${identity}:(F)" 'SYSTEM:(F)' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not protect local configuration: $secretPath" }
}
Write-Host 'Generated local credentials and database initialization script; secret values are not printed.'
