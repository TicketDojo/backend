# 락 조합 성능 테스트 결과 분석 보고서

**테스트 일시**: 2026-01-17
**테스트 환경**: 100 VUs, 1분 20초 (ramp-up 10초, steady 1분, ramp-down 10초)
**테스트 도구**: k6 (Docker)

---

## 1. 테스트 개요

### 목적
Spring Boot 티켓팅 시스템에서 동시성 제어를 위한 다양한 DB 락 전략의 성능을 비교 분석합니다.

### 테스트 대상 락 전략
| 전략 | 설명 |
|------|------|
| **NONE** | 락 없음 (베이스라인) |
| **NAMED** | MySQL GET_LOCK/RELEASE_LOCK |
| **PESSIMISTIC** | SELECT FOR UPDATE |
| **OPTIMISTIC** | @Version 기반 낙관적 락 |

### 테스트 도메인
- **Queue**: 대기열 진입 및 순번 할당
- **Seat**: 좌석 점유
- **Reservation**: 예약 상태 변경

---

## 2. 테스트 결과 요약

### 2.1 Queue 도메인 독립 테스트 (Q1-Q3)

| 테스트 | Queue Lock | TPS | Avg (ms) | P95 (ms) | 에러율 |
|--------|------------|-----|----------|----------|--------|
| Q1 | NONE | 30.4 | 162 | 504 | 2.9% |
| Q2 | NAMED | 8.98 | 1,965 | 524 | 9.7% |
| Q3 | PESSIMISTIC | 23.79 | 94 | 248 | 42.8% |

**분석**:
- NAMED Lock이 가장 큰 성능 저하 (-70% TPS)
- PESSIMISTIC은 빠르지만 락 경합으로 에러율이 매우 높음
- NONE이 가장 안정적인 성능

### 2.2 조합 테스트 (C1-C5)

| 테스트 | Queue | Seat | Reservation | TPS | Avg (ms) | P95 (ms) | 에러율 |
|--------|-------|------|-------------|-----|----------|----------|--------|
| C1 | NONE | NONE | NONE | 26.15 | 139 | 370 | 4.85% |
| C2 | NAMED | PESSIMISTIC | OPTIMISTIC | 19.12 | 589 | 756 | 6.2% |
| C3 | PESSIMISTIC | PESSIMISTIC | PESSIMISTIC | 23.78 | 177 | 522 | 35.4% |
| C4 | NAMED | NAMED | NAMED | 15.48 | 1,407 | 657 | 7.3% |
| C5 | NONE | OPTIMISTIC | OPTIMISTIC | **30.02** | 224 | 584 | **4.02%** |

---

## 3. 성능 순위

### 3.1 TPS (처리량) 순위
```
1. C5 (NONE+OPTIMISTIC+OPTIMISTIC)  : 30.02 TPS  ████████████████████ 100%
2. C1 (All NONE)                     : 26.15 TPS  █████████████████    87%
3. C3 (All PESSIMISTIC)              : 23.78 TPS  ████████████████     79%
4. C2 (현재 구현)                    : 19.12 TPS  █████████████        64%
5. C4 (All NAMED)                    : 15.48 TPS  ██████████           52%
```

### 3.2 응답시간 순위 (낮을수록 좋음)
```
1. C1 (All NONE)                     : 139ms   █████
2. C3 (All PESSIMISTIC)              : 177ms   ██████
3. C5 (NONE+OPTIMISTIC+OPTIMISTIC)   : 224ms   ████████
4. C2 (현재 구현)                    : 589ms   ████████████████████
5. C4 (All NAMED)                    : 1,407ms ████████████████████████████████████████████████
```

### 3.3 안정성 순위 (에러율 낮을수록 좋음)
```
1. C5 (NONE+OPTIMISTIC+OPTIMISTIC)   : 4.02%  ██
2. C1 (All NONE)                     : 4.85%  ██
3. C2 (현재 구현)                    : 6.2%   ███
4. C4 (All NAMED)                    : 7.3%   ████
5. C3 (All PESSIMISTIC)              : 35.4%  ██████████████████
```

---

## 4. 락 전략별 특성 분석

### 4.1 NAMED Lock (MySQL GET_LOCK)
| 항목 | 평가 |
|------|------|
| 성능 영향 | **심각** (-40~70% TPS) |
| 응답시간 | **매우 느림** (10배 이상 증가) |
| 에러율 | 중간 (6~10%) |
| 정합성 | 보장됨 |

**원인 분석**:
- 글로벌 락으로 모든 요청이 직렬화됨
- 락 획득 대기 시간이 누적됨
- 커넥션 풀 고갈 위험

### 4.2 PESSIMISTIC Lock (SELECT FOR UPDATE)
| 항목 | 평가 |
|------|------|
| 성능 영향 | 중간 (-10~20% TPS) |
| 응답시간 | 빠름 (베이스라인과 유사) |
| 에러율 | **매우 높음** (30~40%) |
| 정합성 | 보장됨 |

**원인 분석**:
- Row-Level Lock으로 병렬 처리 가능
- 동일 리소스 경합 시 타임아웃/데드락 발생
- 락 타임아웃(3초) 초과 시 실패

### 4.3 OPTIMISTIC Lock (@Version)
| 항목 | 평가 |
|------|------|
| 성능 영향 | **거의 없음** |
| 응답시간 | 빠름 |
| 에러율 | 낮음 |
| 정합성 | 충돌 시 재시도 필요 |

**원인 분석**:
- 읽기 시 락 없음
- 쓰기 시 버전 체크만 수행
- 충돌 발생 시 OptimisticLockException

---

## 5. 권장 설정

### 5.1 성능 최우선 (C5 설정)
```properties
lock.strategy.queue=NONE
lock.strategy.seat=OPTIMISTIC
lock.strategy.reservation=OPTIMISTIC
```
- **TPS**: 30.02 (최고)
- **에러율**: 4.02% (최저)
- **주의**: Queue 순번 중복 가능성 있음

### 5.2 균형 잡힌 설정 (권장)
```properties
lock.strategy.queue=PESSIMISTIC
lock.strategy.seat=OPTIMISTIC
lock.strategy.reservation=OPTIMISTIC
```
- Queue에서 순번 정합성 보장
- Seat/Reservation은 낙관적 락으로 성능 유지
- 예상 TPS: 25~28

### 5.3 정합성 최우선
```properties
lock.strategy.queue=NAMED
lock.strategy.seat=PESSIMISTIC
lock.strategy.reservation=OPTIMISTIC
```
- 현재 구현 (C2)
- 정합성은 보장되나 성능 저하 (-35%)

---

## 6. 현재 구현(C2)의 개선 방안

### 문제점
1. **Queue의 NAMED Lock이 병목**
   - 평균 응답시간 589ms (NONE 대비 4.2배)
   - TPS 19.12 (NONE 대비 -27%)

### 개선 제안

#### Option 1: Queue Lock을 PESSIMISTIC으로 변경
```properties
lock.strategy.queue=PESSIMISTIC  # NAMED -> PESSIMISTIC
lock.strategy.seat=PESSIMISTIC
lock.strategy.reservation=OPTIMISTIC
```
- 예상 효과: TPS +20%, 응답시간 -60%
- 단점: 에러율 증가 가능

#### Option 2: Queue Lock을 NONE으로 변경 + 애플리케이션 레벨 중복 처리
```properties
lock.strategy.queue=NONE  # NAMED -> NONE
lock.strategy.seat=PESSIMISTIC
lock.strategy.reservation=OPTIMISTIC
```
- 예상 효과: TPS +30%, 응답시간 -70%
- 순번 중복은 Unique Constraint + Retry로 처리

#### Option 3: Redis 분산 락 도입 (장기)
- MySQL Named Lock 대신 Redis 분산 락 사용
- 더 빠른 락 획득/해제
- 확장성 향상

---

## 7. 결론

### 핵심 발견
1. **Named Lock은 성능에 치명적** - 가능하면 사용 자제
2. **Optimistic Lock이 가장 효율적** - 충돌이 적은 도메인에 적합
3. **Pessimistic Lock은 속도는 빠르나 에러율 높음** - 재시도 로직 필수

### 최종 권장
| 도메인 | 권장 전략 | 이유 |
|--------|----------|------|
| Queue | PESSIMISTIC 또는 NONE | Named Lock의 성능 저하 회피 |
| Seat | OPTIMISTIC | WebSocket 기반으로 충돌 적음 |
| Reservation | OPTIMISTIC | 상태 변경 시 충돌 감지 |

---

## 부록: 테스트 데이터 상세

### 테스트 파일 위치
```
D:\spring\backend\performance-tests\results\combinations\
├── Q1-queue-summary.json
├── Q2-queue-summary.json
├── Q3-queue-summary.json
├── C1-queue-summary.json
├── C2-queue-summary.json
├── C3-queue-summary.json
├── C4-queue-summary.json
├── C5-queue-summary.json
└── ANALYSIS-REPORT.md (이 파일)
```

### 설정 변경 방법
`src/main/resources/application.properties`:
```properties
# 락 전략 설정 (NONE, NAMED, PESSIMISTIC, OPTIMISTIC)
lock.strategy.queue=NAMED
lock.strategy.seat=PESSIMISTIC
lock.strategy.reservation=OPTIMISTIC
```

---

*이 보고서는 k6 성능 테스트 결과를 기반으로 자동 생성되었습니다.*
