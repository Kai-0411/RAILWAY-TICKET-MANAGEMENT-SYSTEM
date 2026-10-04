$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceDirectory = Join-Path $projectRoot 'src'
$outputDirectory = Join-Path $projectRoot 'out'
$sources = @(Get-ChildItem -LiteralPath $sourceDirectory -Recurse -File -Filter '*.java' |
    ForEach-Object { $_.FullName })

if ($sources.Count -eq 0) {
    throw "No Java source files found under '$sourceDirectory'."
}

New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
& javac -d $outputDirectory $sources
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& java -cp $outputDirectory com.railway.Main
exit $LASTEXITCODE
