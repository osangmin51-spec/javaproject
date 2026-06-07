param(
    [int]$Port = 8080
)

Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not (Test-Path out)) {
    New-Item -ItemType Directory -Force out | Out-Null
}

$sources = Get-ChildItem -Path . -Filter *.java | Where-Object { $_.Name -ne "MockStockApp.java" } | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d out $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -cp out MiniProjectApp $Port
