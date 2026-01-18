# 락 조합 성능 테스트 결과 분석 스크립트
# 사용법: .\analyze-results.ps1 [-OutputFormat <table|csv|json|markdown>]

param(
    [string]$OutputFormat = "table",
    [string]$ResultsDir = "D:\spring\backend\performance-tests\results\combinations"
)

$ErrorActionPreference = "Stop"

# 결과 파일 수집
$resultFiles = Get-ChildItem -Path $ResultsDir -Filter "*-summary.json" -ErrorAction SilentlyContinue

if ($resultFiles.Count -eq 0) {
    Write-Host "No result files found in $ResultsDir" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=========================================="
Write-Host "  Lock Combination Test Results Analysis" -ForegroundColor Magenta
Write-Host "=========================================="
Write-Host ""
Write-Host "Found $($resultFiles.Count) result files"
Write-Host ""

# 결과 파싱
$allResults = @()

foreach ($file in $resultFiles) {
    try {
        $json = Get-Content $file.FullName | ConvertFrom-Json

        # 파일명에서 테스트 ID와 타입 추출 (예: Q1-queue-summary.json)
        $fileName = $file.BaseName -replace "-summary", ""
        $parts = $fileName -split "-"
        $testId = $parts[0]
        $testType = if ($parts.Count -gt 1) { $parts[1] } else { "unknown" }

        # 메트릭 추출
        $metrics = $json.metrics

        $result = [PSCustomObject]@{
            TestId       = $testId
            TestType     = $testType
            Iterations   = if ($metrics.iterations) { $metrics.iterations.values.count } else { 0 }
            TPS          = if ($metrics.iterations) { [math]::Round($metrics.iterations.values.rate, 2) } else { 0 }
            AvgMs        = if ($metrics.http_req_duration) { [math]::Round($metrics.http_req_duration.values.avg, 2) } else { 0 }
            P50Ms        = if ($metrics.http_req_duration) { [math]::Round($metrics.http_req_duration.values.'p(50)', 2) } else { 0 }
            P95Ms        = if ($metrics.http_req_duration) { [math]::Round($metrics.http_req_duration.values.'p(95)', 2) } else { 0 }
            P99Ms        = if ($metrics.http_req_duration) { [math]::Round($metrics.http_req_duration.values.'p(99)', 2) } else { 0 }
            MaxMs        = if ($metrics.http_req_duration) { [math]::Round($metrics.http_req_duration.values.max, 2) } else { 0 }
            ErrorRate    = if ($metrics.http_req_failed) { [math]::Round($metrics.http_req_failed.values.rate * 100, 2) } else { 0 }
            File         = $file.Name
        }

        $allResults += $result
    }
    catch {
        Write-Host "  Error parsing $($file.Name): $_" -ForegroundColor Yellow
    }
}

# 결과 정렬 (TestId 순)
$allResults = $allResults | Sort-Object TestId, TestType

# 출력 형식에 따라 처리
switch ($OutputFormat.ToLower()) {
    "table" {
        Write-Host ""
        Write-Host "Performance Results (sorted by TestId):" -ForegroundColor Cyan
        Write-Host ""
        $allResults | Format-Table -AutoSize -Property TestId, TestType, TPS, AvgMs, P50Ms, P95Ms, P99Ms, ErrorRate

        # 도메인별 최적 락 전략 분석
        Write-Host ""
        Write-Host "=========================================="
        Write-Host "Optimal Lock Strategy by Domain" -ForegroundColor Green
        Write-Host "=========================================="
        Write-Host ""

        # Queue 도메인 분석
        $queueResults = $allResults | Where-Object { $_.TestId -like "Q*" -and $_.TestType -eq "queue" }
        if ($queueResults) {
            $bestQueue = $queueResults | Sort-Object TPS -Descending | Select-Object -First 1
            Write-Host "Queue Domain:"
            Write-Host "  Best TPS: $($bestQueue.TestId) with $($bestQueue.TPS) TPS (Avg: $($bestQueue.AvgMs)ms)"
        }

        # Seat 도메인 분석
        $seatResults = $allResults | Where-Object { $_.TestId -like "S*" -and $_.TestType -eq "seat" }
        if ($seatResults) {
            $bestSeat = $seatResults | Sort-Object TPS -Descending | Select-Object -First 1
            Write-Host "Seat Domain:"
            Write-Host "  Best TPS: $($bestSeat.TestId) with $($bestSeat.TPS) TPS (Avg: $($bestSeat.AvgMs)ms)"
        }

        # Reservation 도메인 분석
        $reservationResults = $allResults | Where-Object { $_.TestId -like "R*" -and $_.TestType -eq "reservation" }
        if ($reservationResults) {
            $bestReservation = $reservationResults | Sort-Object TPS -Descending | Select-Object -First 1
            Write-Host "Reservation Domain:"
            Write-Host "  Best TPS: $($bestReservation.TestId) with $($bestReservation.TPS) TPS (Avg: $($bestReservation.AvgMs)ms)"
        }

        # 조합 테스트 분석
        $comboResults = $allResults | Where-Object { $_.TestId -like "C*" -and $_.TestType -eq "integrated" }
        if ($comboResults) {
            $bestCombo = $comboResults | Sort-Object TPS -Descending | Select-Object -First 1
            Write-Host ""
            Write-Host "Best Combination (Integrated):"
            Write-Host "  $($bestCombo.TestId) with $($bestCombo.TPS) TPS (Avg: $($bestCombo.AvgMs)ms)"
        }
    }

    "csv" {
        $csvPath = Join-Path $ResultsDir "analysis-results.csv"
        $allResults | Export-Csv -Path $csvPath -NoTypeInformation -Encoding UTF8
        Write-Host "Results exported to: $csvPath" -ForegroundColor Green
    }

    "json" {
        $jsonPath = Join-Path $ResultsDir "analysis-results.json"
        $allResults | ConvertTo-Json -Depth 10 | Set-Content $jsonPath -Encoding UTF8
        Write-Host "Results exported to: $jsonPath" -ForegroundColor Green
    }

    "markdown" {
        $mdPath = Join-Path $ResultsDir "analysis-results.md"

        $mdContent = @"
# Lock Combination Performance Test Results

Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

## Summary Table

| Test ID | Type | TPS | Avg (ms) | P50 (ms) | P95 (ms) | P99 (ms) | Error % |
|---------|------|-----|----------|----------|----------|----------|---------|
"@

        foreach ($r in $allResults) {
            $mdContent += "`n| $($r.TestId) | $($r.TestType) | $($r.TPS) | $($r.AvgMs) | $($r.P50Ms) | $($r.P95Ms) | $($r.P99Ms) | $($r.ErrorRate) |"
        }

        $mdContent += @"

## Analysis by Domain

### Queue Domain (Q1-Q3)

| Test | Lock Strategy | TPS | Avg (ms) | P95 (ms) |
|------|---------------|-----|----------|----------|
"@

        $queueResults = $allResults | Where-Object { $_.TestId -like "Q*" -and $_.TestType -eq "queue" }
        foreach ($r in $queueResults) {
            $strategy = switch ($r.TestId) {
                "Q1" { "NONE" }
                "Q2" { "NAMED" }
                "Q3" { "PESSIMISTIC" }
                default { "Unknown" }
            }
            $mdContent += "`n| $($r.TestId) | $strategy | $($r.TPS) | $($r.AvgMs) | $($r.P95Ms) |"
        }

        $mdContent += @"


### Seat Domain (S1-S4)

| Test | Lock Strategy | TPS | Avg (ms) | P95 (ms) |
|------|---------------|-----|----------|----------|
"@

        $seatResults = $allResults | Where-Object { $_.TestId -like "S*" -and $_.TestType -eq "seat" }
        foreach ($r in $seatResults) {
            $strategy = switch ($r.TestId) {
                "S1" { "NONE" }
                "S2" { "PESSIMISTIC" }
                "S3" { "NAMED" }
                "S4" { "OPTIMISTIC" }
                default { "Unknown" }
            }
            $mdContent += "`n| $($r.TestId) | $strategy | $($r.TPS) | $($r.AvgMs) | $($r.P95Ms) |"
        }

        $mdContent += @"


### Reservation Domain (R1-R4)

| Test | Lock Strategy | TPS | Avg (ms) | P95 (ms) |
|------|---------------|-----|----------|----------|
"@

        $reservationResults = $allResults | Where-Object { $_.TestId -like "R*" -and $_.TestType -eq "reservation" }
        foreach ($r in $reservationResults) {
            $strategy = switch ($r.TestId) {
                "R1" { "NONE" }
                "R2" { "OPTIMISTIC" }
                "R3" { "PESSIMISTIC" }
                "R4" { "NAMED" }
                default { "Unknown" }
            }
            $mdContent += "`n| $($r.TestId) | $strategy | $($r.TPS) | $($r.AvgMs) | $($r.P95Ms) |"
        }

        $mdContent += @"


### Combined Strategies (C1-C8)

| Test | Queue | Seat | Reservation | TPS | Avg (ms) | P95 (ms) |
|------|-------|------|-------------|-----|----------|----------|
"@

        $comboMapping = @{
            "C1" = @{queue="NONE"; seat="NONE"; reservation="NONE"}
            "C2" = @{queue="NAMED"; seat="PESSIMISTIC"; reservation="OPTIMISTIC"}
            "C3" = @{queue="PESSIMISTIC"; seat="PESSIMISTIC"; reservation="PESSIMISTIC"}
            "C4" = @{queue="NAMED"; seat="NAMED"; reservation="NAMED"}
            "C5" = @{queue="NONE"; seat="OPTIMISTIC"; reservation="OPTIMISTIC"}
            "C6" = @{queue="PESSIMISTIC"; seat="OPTIMISTIC"; reservation="OPTIMISTIC"}
            "C7" = @{queue="NAMED"; seat="OPTIMISTIC"; reservation="PESSIMISTIC"}
            "C8" = @{queue="PESSIMISTIC"; seat="NAMED"; reservation="OPTIMISTIC"}
        }

        $comboResults = $allResults | Where-Object { $_.TestId -like "C*" -and $_.TestType -eq "integrated" }
        foreach ($r in $comboResults) {
            if ($comboMapping.ContainsKey($r.TestId)) {
                $c = $comboMapping[$r.TestId]
                $mdContent += "`n| $($r.TestId) | $($c.queue) | $($c.seat) | $($c.reservation) | $($r.TPS) | $($r.AvgMs) | $($r.P95Ms) |"
            }
        }

        $mdContent += @"


## Recommendations

Based on the test results, the optimal lock strategy for each domain:

1. **Queue Domain**: (analyze Q1-Q3 results)
2. **Seat Domain**: (analyze S1-S4 results)
3. **Reservation Domain**: (analyze R1-R4 results)

**Best Overall Combination**: (analyze C1-C8 results)

---
*Generated by Lock Combination Performance Test Suite*
"@

        Set-Content $mdPath -Value $mdContent -Encoding UTF8
        Write-Host "Results exported to: $mdPath" -ForegroundColor Green
    }

    default {
        Write-Host "Unknown output format: $OutputFormat" -ForegroundColor Red
        Write-Host "Supported formats: table, csv, json, markdown"
    }
}

Write-Host ""
Write-Host "=========================================="
Write-Host "Analysis Complete!" -ForegroundColor Green
Write-Host "=========================================="
