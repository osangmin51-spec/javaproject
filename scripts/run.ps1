param(
    [int]$Port = 8080
)

Set-Location (Split-Path -Parent $PSScriptRoot)

$requiredMysqlEnv = @("MYSQL_URL", "MYSQL_USER", "MYSQL_PASSWORD")
$missingMysqlEnv = $requiredMysqlEnv | Where-Object {
    [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_))
}
if ($missingMysqlEnv) {
    Write-Error "Missing required MySQL environment variables: $($missingMysqlEnv -join ', ')"
    exit 1
}

if (-not (Test-Path out)) {
    New-Item -ItemType Directory -Force out | Out-Null
}

$sources = Get-ChildItem -Path "src\main\java" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d out $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -cp "out;lib/*" app.MiniProjectApp $Port
