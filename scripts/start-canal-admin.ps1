param(
    [string]$ContainerName = "canal-admin",
    [string]$Image = "canal/canal-admin:v1.1.8",
    [int]$Port = 8089,
    [string]$MySqlHost = "127.0.0.1",
    [string]$MySqlHostFromContainer = "host.docker.internal",
    [int]$MySqlPort = 3306,
    [string]$Database = "canal_manager",
    [string]$MySqlUser = "root",
    [string]$MySqlPassword = $env:DB_PASSWORD,
    [switch]$Reinitialize
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$AppProperties = Join-Path $Root "src\main\resources\application.properties"
$SqlPath = Join-Path $Root "canal\admin\canal_manager.sql"

function Get-AppDefaultDbPassword {
    if (-not (Test-Path $AppProperties)) {
        return $null
    }

    $line = Get-Content $AppProperties |
        Where-Object { $_ -like "spring.datasource.password=*" } |
        Select-Object -First 1

    if (-not $line) {
        return $null
    }

    if ($line -match '^spring\.datasource\.password=\$\{DB_PASSWORD:(.*)\}$') {
        return $Matches[1]
    }

    return ($line -replace '^spring\.datasource\.password=', '')
}

if ([string]::IsNullOrWhiteSpace($MySqlPassword)) {
    $MySqlPassword = Get-AppDefaultDbPassword
}

if ([string]::IsNullOrWhiteSpace($MySqlPassword)) {
    throw "MySQL password was not provided. Set DB_PASSWORD or pass -MySqlPassword."
}

docker image inspect $Image | Out-Null

if (-not (Test-Path $SqlPath)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $SqlPath) | Out-Null
    $tempContainer = docker create $Image
    try {
        docker cp "${tempContainer}:/home/admin/canal-admin/conf/canal_manager.sql" $SqlPath
    }
    finally {
        docker rm -f $tempContainer | Out-Null
    }
}

$env:MYSQL_PWD = $MySqlPassword
$dbExists = mysql `
    --host=$MySqlHost `
    --port=$MySqlPort `
    --user=$MySqlUser `
    --skip-column-names `
    --execute="SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='$Database';"

$shouldInitialize = $Reinitialize -or [string]::IsNullOrWhiteSpace($dbExists)

if (-not $shouldInitialize) {
    $tableCount = mysql `
        --host=$MySqlHost `
        --port=$MySqlPort `
        --user=$MySqlUser `
        --skip-column-names `
        --execute="SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='$Database';"
    $shouldInitialize = [int]$tableCount -eq 0
}

if ($shouldInitialize) {
    $sourcePath = $SqlPath.Replace("\", "/")
    mysql `
        --host=$MySqlHost `
        --port=$MySqlPort `
        --user=$MySqlUser `
        --execute="source $sourcePath"
    Write-Host "Initialized MySQL database '$Database'."
}
else {
    Write-Host "MySQL database '$Database' already exists; skipped initialization."
}

$existing = docker ps -a --filter "name=^/${ContainerName}$" --format "{{.Names}}"
if ($existing -eq $ContainerName) {
    docker start $ContainerName | Out-Null
    Write-Host "Started existing container '$ContainerName'."
}
else {
    docker run -d `
        --name $ContainerName `
        -p "${Port}:8089" `
        -m 1024m `
        -e server.port=8089 `
        -e canal.adminUser=admin `
        -e canal.adminPasswd=admin `
        -e spring.datasource.address="${MySqlHostFromContainer}:${MySqlPort}" `
        -e spring.datasource.database=$Database `
        -e spring.datasource.username=$MySqlUser `
        -e spring.datasource.password=$MySqlPassword `
        $Image | Out-Null
    Write-Host "Created and started container '$ContainerName'."
}

Write-Host "Canal Admin is available at http://localhost:$Port/"
Write-Host "Default UI login: admin / 123456"
