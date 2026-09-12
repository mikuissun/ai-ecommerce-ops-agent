$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

if (Test-Path $envFile) {
    Get-Content $envFile | Where-Object { $_ -match '^\s*[^#][^=]*=' } | ForEach-Object {
        $name, $value = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim())
    }
} else {
    Write-Warning '.env 不存在，将使用 docker-compose 默认值。'
}

$composeFile = Join-Path $root 'deploy/docker-compose.yml'
$composeArgs = @('-f', $composeFile)
if (Test-Path $envFile) { $composeArgs = @('--env-file', $envFile, '-f', $composeFile) }
docker compose @composeArgs up -d mysql
if ($LASTEXITCODE -ne 0) { throw 'MySQL 容器启动失败。' }

Write-Host '等待 MySQL healthy...'
for ($i = 0; $i -lt 30; $i++) {
    $status = docker inspect --format '{{.State.Health.Status}}' ai-ecommerce-ops-agent-mysql 2>$null
    if ($status -eq 'healthy') { break }
    Start-Sleep -Seconds 2
}
if ($status -ne 'healthy') { throw 'MySQL did not become healthy in time.' }

Push-Location (Join-Path $root 'backend')
try { mvn spring-boot:run } finally { Pop-Location }
