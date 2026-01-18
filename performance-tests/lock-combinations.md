# 락 조합 테스트 매트릭스

## 테스트 가능한 락 전략

| 도메인 | 옵션 | 설명 |
|--------|------|------|
| **대기열 (Queue)** | NONE | 락 없음 (베이스라인) |
| | NAMED | MySQL Named Lock |
| | PESSIMISTIC | SELECT FOR UPDATE |
| **좌석 (Seat)** | NONE | 락 없음 |
| | PESSIMISTIC | SELECT FOR UPDATE (현재 기본) |
| | NAMED | MySQL Named Lock |
| | OPTIMISTIC | @Version 기반 |
| **예약 (Reservation)** | NONE | 락 없음 |
| | OPTIMISTIC | @Version 기반 (현재 기본) |
| | PESSIMISTIC | SELECT FOR UPDATE |
| | NAMED | MySQL Named Lock |

## 총 조합 수

- 대기열: 3가지
- 좌석: 4가지
- 예약: 4가지
- **총: 3 × 4 × 4 = 48가지**

## 테스트 계획

### Phase 1: 도메인별 독립 테스트 (11가지)

각 도메인을 독립적으로 테스트하여 최적 락 전략 파악

| 테스트 ID | Queue | Seat | Reservation | 목적 |
|-----------|-------|------|-------------|------|
| Q1 | NONE | PESSIMISTIC | OPTIMISTIC | 대기열 베이스라인 |
| Q2 | NAMED | PESSIMISTIC | OPTIMISTIC | 대기열 Named Lock |
| Q3 | PESSIMISTIC | PESSIMISTIC | OPTIMISTIC | 대기열 Pessimistic Lock |
| S1 | NAMED | NONE | OPTIMISTIC | 좌석 베이스라인 |
| S2 | NAMED | PESSIMISTIC | OPTIMISTIC | 좌석 Pessimistic Lock |
| S3 | NAMED | NAMED | OPTIMISTIC | 좌석 Named Lock |
| S4 | NAMED | OPTIMISTIC | OPTIMISTIC | 좌석 Optimistic Lock |
| R1 | NAMED | PESSIMISTIC | NONE | 예약 베이스라인 |
| R2 | NAMED | PESSIMISTIC | OPTIMISTIC | 예약 Optimistic Lock |
| R3 | NAMED | PESSIMISTIC | PESSIMISTIC | 예약 Pessimistic Lock |
| R4 | NAMED | PESSIMISTIC | NAMED | 예약 Named Lock |

### Phase 2: 주요 조합 테스트 (8가지)

| 테스트 ID | Queue | Seat | Reservation | 설명 |
|-----------|-------|------|-------------|------|
| C1 | NONE | NONE | NONE | 완전 베이스라인 (락 없음) |
| C2 | NAMED | PESSIMISTIC | OPTIMISTIC | 현재 구현 (권장 설정) |
| C3 | PESSIMISTIC | PESSIMISTIC | PESSIMISTIC | 모두 비관적 락 |
| C4 | NAMED | NAMED | NAMED | 모두 Named 락 |
| C5 | NONE | OPTIMISTIC | OPTIMISTIC | 최소 락 (낙관적만) |
| C6 | PESSIMISTIC | OPTIMISTIC | OPTIMISTIC | Queue만 비관적 |
| C7 | NAMED | OPTIMISTIC | PESSIMISTIC | 혼합 전략 1 |
| C8 | PESSIMISTIC | NAMED | OPTIMISTIC | 혼합 전략 2 |

## 측정 지표

각 테스트에서 수집할 지표:
- **TPS (Throughput)**: 초당 처리 요청 수
- **avg**: 평균 응답 시간
- **p95**: 95번째 백분위 응답 시간
- **p99**: 99번째 백분위 응답 시간
- **에러율**: 실패한 요청 비율
- **정합성**: 데이터 무결성 (중복 순번, 이중 예약 등)

## 테스트 환경

- VUs: 100
- Duration: 1분
- Ramp-up: 10초
- Ramp-down: 10초
