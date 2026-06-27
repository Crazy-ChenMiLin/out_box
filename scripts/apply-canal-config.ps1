param(
    [string]$ContainerName = "canal-server"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$BackupDir = Join-Path $Root ("canal\backup\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

docker cp "${ContainerName}:/home/admin/canal-server/conf/canal.properties" (Join-Path $BackupDir "canal.properties")
docker cp "${ContainerName}:/home/admin/canal-server/conf/example/instance.properties" (Join-Path $BackupDir "instance.properties")

docker cp (Join-Path $Root "canal\conf\canal.properties") "${ContainerName}:/home/admin/canal-server/conf/canal.properties"
docker cp (Join-Path $Root "canal\conf\example\instance.properties") "${ContainerName}:/home/admin/canal-server/conf/example/instance.properties"
docker restart $ContainerName

Write-Host "Canal config applied to $ContainerName. Backup saved to $BackupDir"
