# 락 조합 성능 테스트 자동화 스크립트
# 사용법: .\run-all-tests.ps1 [-SkipConfirmation] [-TestType <all|queue|seat|reservation|integrated>]

param(
    [switch]$SkipConfirmation,
    [string]$TestType = "all"
)

$ErrorActionPreference = "Stop"

# 설정
$BASE_URL = "http://host.docker.internal:8080"
$RESULTS_DIR = "D:\spring\backend\performance-tests\results\combinations"
$APP_PROPERTIES = "D:\spring\backend\src\main\resources\application.properties"
$SCRIPTS_DIR = "D:\spring\backend\performance-tests\scripts"

# 테스트 스크립트 정의
$testScripts = @{
    queue       = "02-queue-entry-test.js"
    seat        = "04-seat-hold-test.js"
    reservation = "03-reservation-test.js"
    integrated  = "05-integrated-flow-test.js"
}

# 테스트 조합 정의 (Queue, Seat, Reservation)
$testCombinations = @(
    # Phase 1: 도메인별 독립 테스트 - Queue
    @{id="Q1"; queue="NONE"; seat="PESSIMISTIC"; reservation="OPTIMISTIC"; desc="Queue NONE (baseline)"; focus="queue"},
    @{id="Q2"; queue="NAMED"; seat="PESSIMISTIC"; reservation="OPTIMISTIC"; desc="Queue NAMED"; focus="queue"},
    @{id="Q3"; queue="PESSIMISTIC"; seat="PESSIMISTIC"; reservation="OPTIMISTIC"; desc="Queue PESSIMISTIC"; focus="queue"},

    # Phase 1: 도메인별 독립 테스트 - Seat
    @{id="S1"; queue="NAMED"; seat="NONE"; reservation="OPTIMISTIC"; desc="Seat NONE (baseline)"; focus="seat"},
    @{id="S2"; queue="NAMED"; seat="PESSIMISTIC"; reservation="OPTIMISTIC"; desc="Seat PESSIMISTIC"; focus="seat"},
    @{id="S3"; queue="NAMED"; seat="NAMED"; reservation="OPTIMISTIC"; desc="Seat NAMED"; focus="seat"},
    @{id="S4"; queue="NAMED"; seat="OPTIMISTIC"; reservation="OPTIMISTIC"; desc="Seat OPTIMISTIC"; focus="seat"},

    # Phase 1: 도메인별 독립 테스트 - Reservation
    @{id="R1"; queue="NAMED"; seat="PESSIMISTIC"; reservation="NONE"; desc="Reservation NONE (baseline)"; focus="reservation"},
    @{id="R2"; queue="NAMED"; seat="PESSIMISTIC"; reservation="OPTIMISTIC"; desc="Reservation OPTIMISTIC"; focus="reservation"},
    @{id="R3"; queue="NAMED"; seat="PESSIMISTIC"; reservation="PESSIMISTIC"; desc="Reservation PESSIMISTIC"; focus="reservation"},
    @{id="R4"; queue="NAMED"; seat="PESSIMISTIC"; reservation="NAMED"; desc="Reservation NAMED"; focus="reservation"},

    # Phase 2: 주요 조합 테스트
    @{id="C1"; queue="NONE"; seat="NONE"; reservation="NONE"; desc="All NONE (baseline)"; focus="integrated"},
    @{id="C2"; queue="NAMED"; seat="PESSIMISTIC"; reservation="OPTIMISTIC"; desc="Current implementation"; focus="integrated"},
    @{id="C3"; queue="PESSIMISTIC"; seat="PESSIMISTIC"; reservation="PESSIMISTIC"; desc="All PESSIMISTIC"; focus="integrated"},
    @{id="C4"; queue="NAMED"; seat="NAMED"; reservation="NAMED"; desc="All NAMED"; focus="integrated"},
    @{id="C5"; queue="NONE"; seat="OPTIMISTIC"; reservation="OPTIMISTIC"; desc="Minimal locks"; focus="integrated"},
    @{id="C6"; queue="PESSIMISTIC"; seat="OPTIMISTIC"; reservation="OPTIMISTIC"; desc="Queue PESSIMISTIC + others OPTIMISTIC"; focus="integrated"},
    @{id="C7"; queue="NAMED"; seat="OPTIMISTIC"; reservation="PESSIMISTIC"; desc="Mixed strategy 1"; focus="integrated"},
    @{id="C8"; queue="PESSIMISTIC"; seat="NAMED"; reservation="OPTIMISTIC"; desc="Mixed strategy 2"; focus="integrated"}
)

function Update-LockStrategy {
    param(
        [string]$queue,
        [string]$seat,
        [string]$reservation
    )

    $content = Get-Content $APP_PROPERTIES -Raw

    # 락 전략 업데이트
    $content = $content -replace "lock\.strategy\.queue=\w+", "lock.strategy.queue=$queue"
    $content = $content -replace "lock\.strategy\.seat=\w+", "lock.strategy.seat=$seat"
    $content = $content -replace "lock\.strategy\.reservation=\w+", "lock.strategy.reservation=$reservation"

    Set-Content $APP_PROPERTIES -Value $content -NoNewline

    Write-Host "  [Config] Queue=$queue, Seat=$seat, Reservation=$reservation" -ForegroundColor Cyan
}

function Wait-ForApplication {
    Write-Host "  Waiting for application..." -NoNewline
    $maxRetries = 30
    $retryCount = 0

    while ($retryCount -lt $maxRetries) {
        try {
            $response = Invoke-WebRequest -Uri "$BASE_URL/actuator/health" -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                $health = $response.Content | ConvertFrom-Json
                if ($health.status -eq "UP") {
                    Write-Host " Ready!" -ForegroundColor Green
                    return $true
                }
            }
        } catch {
            # Ignore errors
        }

        Start-Sleep -Seconds 2
        $retryCount++
        Write-Host "." -NoNewline
    }

    Write-Host " TIMEOUT" -ForegroundColor Red
    return $false
}

function Run-SingleTest {
    param(
        [string]$testId,
        [string]$testType,
        [string]$script
    )

    $outputFile = "$RESULTS_DIR\$testId-$testType-summary.json"

    Write-Host "    Running $testType test..." -NoNewline

    # k6 테스트 실행
    $result = docker run --rm -i `
        -v D:\spring\backend\performance-tests:/performance-tests `
        grafana/k6 run `
        /performance-tests/scripts/$script `
        --env BASE_URL=$BASE_URL `
        --summary-export=/performance-tests/results/combinations/$testId-$testType-summary.json `
        2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Host " Done" -ForegroundColor Green
        return $true
    } else {
        Write-Host " Failed" -ForegroundColor Red
        return $false
    }
}

function Run-AllDomainTests {
    param(
        [string]$testId,
        [string]$description
    )

    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Test $testId : $description" -ForegroundColor Yellow
    Write-Host "=========================================="

    $results = @{}

    # 각 도메인 테스트 실행
    foreach ($type in $testScripts.Keys) {
        if ($TestType -eq "all" -or $TestType -eq $type) {
            $script = $testScripts[$type]
            $results[$type] = Run-SingleTest -testId $testId -testType $type -script $script
        }
    }

    return $results
}

function Run-FocusedTest {
    param(
        [string]$testId,
        [string]$description,
        [string]$focus
    )

    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Test $testId : $description" -ForegroundColor Yellow
    Write-Host "=========================================="

    # 포커스된 도메인 + 통합 테스트 실행
    $testsToRun = @($focus, "integrated")
    $results = @{}

    foreach ($type in $testsToRun) {
        if ($testScripts.ContainsKey($type)) {
            $script = $testScripts[$type]
            $results[$type] = Run-SingleTest -testId $testId -testType $type -script $script
        }
    }

    return $results
}

function Show-Summary {
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Test Execution Summary" -ForegroundColor Green
    Write-Host "=========================================="
    Write-Host ""
    Write-Host "Results saved in: $RESULTS_DIR"
    Write-Host ""
    Write-Host "To analyze results, run:"
    Write-Host "  .\analyze-results.ps1" -ForegroundColor Cyan
    Write-Host ""
}

# 메인 실행
Write-Host ""
Write-Host "=========================================="
Write-Host "  Lock Combination Performance Tests" -ForegroundColor Magenta
Write-Host "=========================================="
Write-Host ""
Write-Host "Total combinations: $($testCombinations.Count)"
Write-Host "Test type: $TestType"
Write-Host ""

# 결과 디렉토리 생성
New-Item -ItemType Directory -Force -Path $RESULTS_DIR | Out-Null

# 현재 시간 기록
$startTime = Get-Date
$completedTests = 0
$failedTests = 0

# 각 조합에 대해 테스트 실행
foreach ($combo in $testCombinations) {
    $testNum = $testCombinations.IndexOf($combo) + 1

    Write-Host ""
    Write-Host "[$testNum/$($testCombinations.Count)] Preparing: $($combo.id)" -ForegroundColor White

    # 1. 락 전략 업데이트
    Update-LockStrategy -queue $combo.queue -seat $combo.seat -reservation $combo.reservation

    # 2. 사용자에게 재시작 요청 (자동 스킵 옵션 제공)
    if (-not $SkipConfirmation) {
        Write-Host ""
        Write-Host "  Please restart Spring Boot and press Enter..." -ForegroundColor Yellow
        Read-Host
    } else {
        Write-Host "  Auto mode - waiting 5 seconds for application restart..." -ForegroundColor DarkGray
        Start-Sleep -Seconds 5
    }

    # 3. 애플리케이션 준비 대기
    if (-not (Wait-ForApplication)) {
        Write-Host "  Skipping test $($combo.id) - application not ready" -ForegroundColor Red
        $failedTests++
        continue
    }

    # 4. 테스트 실행 (포커스 또는 전체)
    if ($combo.focus -eq "integrated" -or $TestType -eq "all") {
        Run-AllDomainTests -testId $combo.id -description $combo.desc
    } else {
        Run-FocusedTest -testId $combo.id -description $combo.desc -focus $combo.focus
    }

    $completedTests++

    Write-Host ""
    Write-Host "  Test $($combo.id) completed!" -ForegroundColor Green
}

# 종료 시간 및 요약
$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "=========================================="
Write-Host "All Tests Completed!" -ForegroundColor Green
Write-Host "=========================================="
Write-Host ""
Write-Host "Duration: $($duration.ToString('hh\:mm\:ss'))"
Write-Host "Completed: $completedTests / $($testCombinations.Count)"
Write-Host "Failed: $failedTests"
Write-Host ""
Write-Host "Results saved in: $RESULTS_DIR"
Write-Host "=========================================="
