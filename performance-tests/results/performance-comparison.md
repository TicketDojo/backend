# 성능 테스트 결과 비교 (Named Lock 영향 분석)

## 테스트 환경

- **테스트 도구**: k6 (Docker)
- **Virtual Users (VUs)**: 100
- **테스트 기간**: 1분 20초 (Ramp-up 10초 + 유지 60초 + Ramp-down 10초)
- **테스트 사용자**: 100명
- **대상 API**: 대기열 진입 (`/queue/jwt/enter`)

## 핵심 지표 비교

| 지표 | 베이스라인 (Lock OFF) | Named Lock (Lock ON) | 변화량 | 변화율 |
|------|---------------------|---------------------|--------|--------|
| **총 반복 실행 횟수** | 3,101 | 533 | -2,568 | **-82.8%** |
| **대기열 진입 평균 시간** | 64.94ms | 4,916.05ms | +4,851ms | **+7,470%** |
| **대기열 진입 p95** | 236ms | 30,600ms | +30,364ms | **+12,968%** |
| **대기열 진입 p90** | 195ms | 30,083ms | +29,888ms | **+15,327%** |
| **HTTP 요청 평균 시간** | 73.11ms | 5,759.76ms | +5,687ms | **+7,779%** |
| **HTTP 요청 p95** | 248.25ms | 30,118.17ms | +29,870ms | **+12,030%** |
| **TPS (초당 요청)** | 99.31 req/s | 13.01 req/s | -86.3 req/s | **-86.9%** |
| **에러율** | 3.22% | 25.51% | +22.29% | **+692%** |
| **평균 반복 시간** | 2,385ms | 14,400ms | +12,015ms | **+504%** |

## 상세 분석

### 1. 처리량 (Throughput) 급감

**베이스라인 (Lock OFF):**
- 총 3,101회 반복 실행
- TPS: 99.31 req/s

**Named Lock (Lock ON):**
- 총 533회 반복 실행 (82.8% 감소)
- TPS: 13.01 req/s (86.9% 감소)

**분석:**
- Named Lock으로 인해 대기열 진입이 완전히 직렬화됨
- 100명의 동시 사용자가 락 획득을 위해 대기하면서 처리량이 1/6 수준으로 감소

### 2. 응답 시간 급증

**대기열 진입 시간:**
- 평균: 64.94ms → 4,916ms (**75.7배 증가**)
- p95: 236ms → 30,600ms (**129.7배 증가**)
- 최대: 428ms → 59,998ms (**140배 증가**)

**분석:**
- Named Lock 타임아웃이 3초로 설정되어 있음
- p95가 30.6초인 것은 대부분의 요청이 타임아웃 근처까지 대기함을 의미
- 일부 요청은 거의 1분(60초)까지 대기

### 3. 에러율 급증

**베이스라인:** 3.22%
**Named Lock:** 25.51% (7.9배 증가)

**에러 상세:**
- 로그인 실패 (401): 80건
- 대기열 진입 실패: 58건
- 응답 시간 초과: 56건

**분석:**
- 락 경합으로 인한 타임아웃 발생
- 장시간 대기로 인한 연결 타임아웃

### 4. 체크 성공률 비교

| 체크 항목 | 베이스라인 성공/실패 | Named Lock 성공/실패 |
|-----------|---------------------|---------------------|
| Login successful | 3,001 / 100 | 484 / 80 |
| Queue entry successful | 3,001 / 0 | 403 / 58 |
| Response time < 1000ms | 3,001 / 0 | 405 / 56 |
| Queue status retrieved | 0 / 3,001 | 0 / 400 |

## 문제점 및 원인 분석

### 1. Named Lock의 범위가 너무 광범위

**현재 구현:**
```java
private Queue createQueueWithLock(User user) {
    return namedLockService.executeWithLock("queue:assign:position", () -> {
        int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);

        if (queuePolicy.canActivateImmediately(activeCount)) {
            return Queue.createActive(user);
        } else {
            return Queue.createWaiting(user);
        }
    });
}
```

**문제:**
- 모든 대기열 진입 요청이 **단일 락 키** `"queue:assign:position"`을 사용
- 100명이 동시에 진입 시도 시 99명이 대기
- 락 획득 타임아웃(3초)에 도달하는 요청 발생

### 2. 락 타임아웃 설정

**현재 설정:**
```java
private static final int DEFAULT_TIMEOUT_SECONDS = 3;
```

**문제:**
- 3초는 높은 동시성 환경에서 너무 짧음
- p95가 30.6초인 것을 보면 많은 요청이 락 획득 재시도를 반복

### 3. 데이터베이스 커넥션 풀 부족 가능성

**현재 설정:**
```properties
spring.datasource.hikari.maximum-pool-size=20
```

**문제:**
- 100 VUs가 동시에 락 획득을 대기하면 커넥션이 부족할 수 있음
- Named Lock은 커넥션을 점유한 상태로 대기

## 권장 사항

### 1. Named Lock 범위 최소화

**현재 (너무 광범위):**
```java
namedLockService.executeWithLock("queue:assign:position", () -> {
    // COUNT 쿼리 + 비즈니스 로직 + INSERT
});
```

**개선안:**
```java
// 락 밖에서 카운트 조회
int activeCount = queueRepository.countByStatus(QueueStatus.ACTIVE);

// 락은 순번 할당 시에만
if (!queuePolicy.canActivateImmediately(activeCount)) {
    namedLockService.executeWithLock("queue:assign:position", () -> {
        // 재확인 후 순번 할당만
        int recheck = queueRepository.countByStatus(QueueStatus.WAITING);
        return assignPosition(recheck);
    });
}
```

### 2. 락 타임아웃 증가

```java
private static final int DEFAULT_TIMEOUT_SECONDS = 10; // 3초 → 10초
```

### 3. 커넥션 풀 크기 증가

```properties
spring.datasource.hikari.maximum-pool-size=50  # 20 → 50
```

### 4. 락 키 세분화 (고급)

```java
// 사용자 ID 기반 분산 락
String lockKey = "queue:assign:" + (userId % 10);
```

- 10개의 락으로 분산하여 동시성 향상

### 5. Redis 분산 락 고려

Named Lock(MySQL)의 한계:
- 단일 DB에 모든 락 요청 집중
- 스케일 아웃 불가

대안: Redisson/Lettuce 기반 Redis 분산 락
- 더 빠른 락 획득/해제
- TTL 자동 관리
- 스케일 아웃 가능

## 결론

### Named Lock의 효과

**✅ 정합성 (Correctness):**
- 대기열 순번 중복 방지 목적은 달성
- Race condition 완전 제거

**❌ 성능 (Performance):**
- **처리량 82.8% 감소**
- **평균 응답시간 75.7배 증가**
- **에러율 7.9배 증가**

### 트레이드오프

현재 구현은 **정합성을 보장**하지만 **성능을 심각하게 희생**합니다.

**실제 운영 환경 적용 시:**
1. **100 VUs는 매우 높은 부하**: 실제 티켓팅에서는 수천~수만 동시 사용자 예상
2. **현재 구현으로는 운영 불가능**: TPS 13 req/s는 실시간 서비스로 부적합
3. **즉시 개선 필요**: 위의 권장 사항 중 최소 1~3번 적용 필수

### 최종 권고

**단기 (즉시 적용):**
- Named Lock 범위 최소화
- 락 타임아웃 10초로 증가
- 커넥션 풀 50으로 증가

**중기 (1주 이내):**
- 락 키 세분화로 동시성 개선
- 비관적 락 추가 적용 검토

**장기 (향후 개발):**
- Redis 분산 락으로 마이그레이션
- 대기열 시스템 아키텍처 재설계 (Token Bucket, Rate Limiter 등)
