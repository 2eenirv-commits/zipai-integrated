param(
    [int]$LockMinutes = 180
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
$RpaDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $RpaDir
$LogDir = Join-Path $RpaDir "logs"
$LockFile = Join-Path $LogDir "happy_housing_crawler.lock"
$Crawler = Join-Path $RpaDir "happy_housing_crawler.py"

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = Join-Path $LogDir ("happy_housing_" + $Stamp + ".log")

if (Test-Path $LockFile) {
    $age = (Get-Date) - (Get-Item $LockFile).LastWriteTime
    if ($age.TotalMinutes -lt $LockMinutes) {
        "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] SKIP: previous crawler lock exists: $LockFile" | Tee-Object -FilePath $LogFile
        exit 3
    }
    Remove-Item -Force $LockFile
}

Set-Content -Path $LockFile -Value ("PID=" + $PID + " START=" + (Get-Date -Format "s")) -Encoding UTF8

try {
    $VenvPython = Join-Path $ProjectRoot ".venv\Scripts\python.exe"
    if (Test-Path $VenvPython) {
        $Python = $VenvPython
    } else {
        $cmd = Get-Command python -ErrorAction SilentlyContinue
        if ($null -eq $cmd) {
            throw "Python을 찾을 수 없습니다. 프로젝트 .venv 또는 PATH를 확인하세요."
        }
        $Python = $cmd.Source
    }

    if (-not (Test-Path $Crawler)) {
        throw "Crawler 파일을 찾을 수 없습니다: $Crawler"
    }

    "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] START" | Tee-Object -FilePath $LogFile
    "Python: $Python" | Tee-Object -FilePath $LogFile -Append
    "Crawler: $Crawler" | Tee-Object -FilePath $LogFile -Append

    # pypdf가 복구 가능한 PDF 구조 경고를 stderr로 출력할 수 있습니다.
    # 이를 PowerShell의 중단 오류로 승격하지 않고 크롤러의 실제 종료 코드로 성공 여부를 판단합니다.
    $PreviousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $Python $Crawler 2>&1 | ForEach-Object { "$_" } | Tee-Object -FilePath $LogFile -Append
    $ExitCode = $LASTEXITCODE
    $ErrorActionPreference = $PreviousErrorActionPreference

    if ($ExitCode -eq 0) {
        "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] SUCCESS exit=$ExitCode" | Tee-Object -FilePath $LogFile -Append
    } else {
        "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] FAILED exit=$ExitCode" | Tee-Object -FilePath $LogFile -Append
    }

    exit $ExitCode
}
catch {
    "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] RUNNER FAILED: $($_.Exception.Message)" | Tee-Object -FilePath $LogFile -Append
    exit 10
}
finally {
    if (Test-Path $LockFile) {
        Remove-Item -Force $LockFile
    }
}
