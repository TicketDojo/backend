# k6 성능 테스트 가이드

## 사전 요구사항

- Docker 설치 및 실행 중
- Spring Boot 애플리케이션이 `localhost:8080`에서 실행 중
- MySQL 데이터베이스 실행 중

## 테스트 스크립트 개요

1. **01-setup-users.js** - 성능 테스트용 100명의 테스트 사용자 생성
2. **02-queue-entry-test.js** - 100 VUs로 1분간 대기열 진입 테스트
3. **03-reservation-test.js** - 예약 생성 및 낙관적 락 충돌 테스트
4. **04-seat-hold-test.js** - 비관적 락을 사용한 좌석 점유 테스트
5. **05-integrated-flow-test.js** - 전체 티켓팅 플로우 시뮬레이션

## Docker 실행 명령어

### 테스트 사용자 설정 (최초 1회 실행)

```bash
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/01-setup-users.js --env BASE_URL=http://host.docker.internal:8080
```

### 개별 테스트 실행

#### 대기열 진입 테스트 (100 VUs, 1분)

```bash
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/02-queue-entry-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/baseline/queue-entry.json
```

#### 예약 테스트 (100 VUs, 1분)

```bash
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/03-reservation-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/baseline/reservation.json
```

#### 좌석 점유 테스트 (100 VUs, 1분)

```bash
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/04-seat-hold-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/baseline/seat-hold.json
```

#### 통합 플로우 테스트 (100 VUs, 1분)

```bash
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/05-integrated-flow-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/baseline/integrated-flow.json
```

## 테스트 절차

### Phase 1: 베이스라인 측정 (네임드 락 비활성화)

1. **코드에서 네임드 락 비활성화:**
   - `NamedLockService.java`에 feature flag 추가 (아래 설정 섹션 참고)
   - `application.properties`에 `named.lock.enabled=false` 설정
   - 애플리케이션 재시작

2. **테스트 실행:**
   ```bash
   # 대기열 진입 테스트
   docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/02-queue-entry-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/baseline/queue-entry.json

   # 통합 플로우 테스트
   docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/05-integrated-flow-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/baseline/integrated-flow.json
   ```

### Phase 2: 락 적용 후 측정 (네임드 락 활성화)

1. **네임드 락 활성화:**
   - `application.properties`에 `named.lock.enabled=true` 설정
   - 애플리케이션 재시작

2. **테스트 실행:**
   ```bash
   # 대기열 진입 테스트
   docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/02-queue-entry-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/with-locks/queue-entry.json

   # 통합 플로우 테스트
   docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/05-integrated-flow-test.js --env BASE_URL=http://host.docker.internal:8080 --out json=/performance-tests/results/with-locks/integrated-flow.json
   ```

### Phase 3: 결과 비교

결과는 JSON 형식으로 저장됩니다:
- `results/baseline/` - 네임드 락 없는 성능
- `results/with-locks/` - 네임드 락 적용 후 성능

비교할 주요 지표:
- **http_req_duration**: 평균, p95, p99 응답 시간
- **errors**: 에러율
- **커스텀 메트릭**:
  - `queue_entry_duration`: 대기열 진입 시간
  - `seat_hold_duration`: 좌석 점유 시간
  - `full_flow_duration`: 엔드투엔드 플로우 시간

## 설정

### 네임드 락 Feature Flag

`src/main/resources/application.properties`에 추가:

```properties
# Named Lock Feature Flag
named.lock.enabled=true
```

`NamedLockService.java` 수정:

```java
@Value("${named.lock.enabled:true}")
private boolean namedLockEnabled;

@Transactional(propagation = Propagation.REQUIRES_NEW)
public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
    if (!namedLockEnabled) {
        // 락 획득 건너뛰고 바로 실행
        return supplier.get();
    }

    // 기존 락 로직
    try {
        // ...
    } finally {
        // ...
    }
}
```

## 예상 성능 영향 (계획서 기준)

| 지표 | 베이스라인 | 네임드 락 적용 | 영향 |
|------|-----------|---------------|------|
| 대기열 진입 평균 응답시간 | 50ms | 70ms | +10-30ms |
| 대기열 진입 p95 | 100ms | 150ms | +50ms |
| 대기열 진입 TPS | 2000 | 1500 | -25% |
| 순번 중복 | 발생 | 없음 | 해결됨 |

## 문제 해결

### Connection Refused 에러

연결 에러가 발생하면:
- Spring Boot 앱이 8080 포트에서 실행 중인지 확인
- Windows에서 Docker 컨테이너에서 호스트 접근 시 `host.docker.internal` 사용 (localhost 아님)

### Permission Denied (볼륨 마운트)

Docker가 볼륨에 접근할 수 없는 경우:
1. Docker Desktop 설정에서 D: 드라이브 공유
2. 절대 경로를 슬래시로 사용: `/d/spring/backend/performance-tests`

### 테스트 사용자를 찾을 수 없음

먼저 setup 스크립트를 실행하세요:
```bash
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/01-setup-users.js --env BASE_URL=http://host.docker.internal:8080
```

## 빠른 테스트 실행 (전체 테스트)

```bash
# 사용자 설정
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/01-setup-users.js --env BASE_URL=http://host.docker.internal:8080

# 모든 테스트 실행
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/02-queue-entry-test.js --env BASE_URL=http://host.docker.internal:8080
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/03-reservation-test.js --env BASE_URL=http://host.docker.internal:8080
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/04-seat-hold-test.js --env BASE_URL=http://host.docker.internal:8080
docker run --rm -i -v D:\spring\backend\performance-tests:/performance-tests grafana/k6 run /performance-tests/scripts/05-integrated-flow-test.js --env BASE_URL=http://host.docker.internal:8080
```

## 주요 API 엔드포인트 (스크립트에서 사용)

테스트 스크립트는 다음 엔드포인트를 사용합니다. 실제 프로젝트의 엔드포인트와 다르다면 스크립트를 수정하세요:

- `POST /api/auth/signup` - 회원가입
- `POST /api/auth/login` - 로그인 (JWT 토큰 획득)
- `POST /api/queue/enter` - 대기열 진입
- `GET /api/queue/status` - 대기열 상태 조회
- `POST /api/ticketing/reservation` - 예약 생성
- `POST /api/ticketing/seat/hold` - 좌석 점유
- `PUT /api/ticketing/reservation/{id}/state` - 예약 상태 변경

실제 엔드포인트가 다르다면 각 스크립트 파일에서 URL을 수정하세요.

---

## 락 조합 테스트 자동화

모든 락 조합을 테스트하기 위한 자동화 스크립트가 제공됩니다.

### 테스트 조합

총 19가지 테스트 조합이 정의되어 있습니다:
- **Q1-Q3**: Queue 도메인 독립 테스트 (NONE, NAMED, PESSIMISTIC)
- **S1-S4**: Seat 도메인 독립 테스트 (NONE, PESSIMISTIC, NAMED, OPTIMISTIC)
- **R1-R4**: Reservation 도메인 독립 테스트 (NONE, OPTIMISTIC, PESSIMISTIC, NAMED)
- **C1-C8**: 조합 테스트 (다양한 락 조합)

자세한 내용은 `lock-combinations.md` 파일을 참조하세요.

### 자동화 스크립트 실행

```powershell
# 전체 조합 테스트 실행 (수동 재시작 필요)
.\run-all-tests.ps1

# 특정 테스트 유형만 실행
.\run-all-tests.ps1 -TestType queue

# 결과 분석
.\analyze-results.ps1
.\analyze-results.ps1 -OutputFormat markdown
.\analyze-results.ps1 -OutputFormat csv
```

### 실행 절차

1. **테스트 시작**
   ```powershell
   cd D:\spring\backend\performance-tests
   .\run-all-tests.ps1
   ```

2. **각 조합마다**:
   - 스크립트가 `application.properties`의 락 전략을 자동 변경
   - "Please restart Spring Boot and press Enter" 메시지 표시
   - Spring Boot 재시작 후 Enter 키 입력
   - k6 테스트 자동 실행

3. **결과 분석**
   ```powershell
   .\analyze-results.ps1 -OutputFormat markdown
   ```

### 결과 파일 구조

```
results/
└── combinations/
    ├── Q1-queue-summary.json      # Queue NONE 테스트 결과
    ├── Q2-queue-summary.json      # Queue NAMED 테스트 결과
    ├── ...
    ├── C1-integrated-summary.json # All NONE 조합 결과
    ├── C2-integrated-summary.json # 현재 구현 조합 결과
    └── analysis-results.md        # 분석 보고서
```

### 설정 변경

`application.properties`에서 락 전략 변경:

```properties
# 락 전략 설정 (NONE, NAMED, PESSIMISTIC, OPTIMISTIC)
lock.strategy.queue=NAMED
lock.strategy.seat=PESSIMISTIC
lock.strategy.reservation=OPTIMISTIC
```

스크립트가 자동으로 이 값들을 변경합니다.
