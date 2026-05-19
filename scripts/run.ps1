param(
    [int]$Port = 8080
)

Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not (Test-Path out)) {
    New-Item -ItemType Directory -Force out | Out-Null
}

javac -encoding UTF-8 -d out MockStockApp.java
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

java -cp out MockStockApp $Port
