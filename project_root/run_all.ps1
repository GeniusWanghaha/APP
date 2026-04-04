param(
    [int]$Phase = 1
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$venvPython = Join-Path $root "env\.venv\Scripts\python.exe"

if (-not (Test-Path $venvPython)) {
    Write-Host "[setup] creating virtual environment..."
    python -m venv (Join-Path $root "env\.venv")
}

Write-Host "[setup] upgrading pip..."
& $venvPython -m pip install --upgrade pip

Write-Host "[setup] installing dependencies..."
& $venvPython -m pip install -r (Join-Path $root "requirements.txt")

Write-Host "[run] starting pipeline phase $Phase ..."
& $venvPython -m src.run_pipeline --phase $Phase

Write-Host "[done] pipeline completed."
