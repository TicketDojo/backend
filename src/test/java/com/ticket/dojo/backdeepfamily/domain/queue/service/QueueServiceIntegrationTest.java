package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.integration.base.BaseServiceIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueueService 통합 테스트
 *
 * 테스트 목적:
 * - QueueService + QueueRepository 통합 검증
 * - 대기열 로직 검증 (50명 정책, 상태 전환, 재진입 등)
 * - Scheduler 동작 검증
 *
 * 테스트 환경:
 * - 실제 Spring Context 로딩
 * - 실제 MySQL 데이터베이스 사용
 * - @Transactional로 테스트 간 격리
 *
 * 주요 테스트 케이스:
 * 1. 1명 진입 - 즉시 ACTIVE
 * 2. 10명 진입 - 모두 ACTIVE
 * 3. 100명 진입 - 50명 ACTIVE, 50명 WAITING
 * 4. 재진입 시 기존 세션 삭제 및 맨 뒤로 이동
 * 5. 결제 진입(만료) 시 대기자 자동 활성화
 * 6. 퇴장 시 대기자 자동 활성화
 * 7. 동일 사용자 중복 세션 방지
 */
@DisplayName("QueueService 통합 테스트")
class QueueServiceIntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private QueueService queueService;

    @BeforeEach
    void setUp() {
        // DB 초기화
        queueRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        queueRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * 테스트 1: 1명 진입 - 즉시 ACTIVE
     *
     * 시나리오:
     * 1. 현재 대기열이 비어있음 (0명)
     * 2. 사용자 1명이 대기열 진입
     * 3. 50명 미만이므로 즉시 ACTIVE 상태로 진입
     *
     * 검증 사항:
     * - 응답의 status가 ACTIVE
     * - 응답의 position이 0 (ACTIVE는 순번 의미 없음)
     * - DB에 ACTIVE 상태로 저장
     * - ACTIVE 카운트 1명
     */
    @Test
    @DisplayName("1명 대기열 진입 - 즉시 ACTIVE 상태")
    @Transactional
    void enterQueue_SingleUser_ActiveImmediately() {
        // given
        User user = createAndSaveTestUser("single");

        // when
        QueueEnterResponse response = queueService.enterQueue(user.getUserId());

        // then
        assertThat(response.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(response.getPosition()).isEqualTo(0);
        assertThat(response.getToken()).isNotNull();
        assertThat(queueRepository.countByStatus(QueueStatus.ACTIVE)).isEqualTo(1);
        assertThat(queueRepository.countByStatus(QueueStatus.WAITING)).isEqualTo(0);
    }

    /**
     * 테스트 2: 10명 진입 - 모두 ACTIVE
     *
     * 시나리오:
     * 1. 10명의 사용자가 순차적으로 대기열 진입
     * 2. 모두 50명 미만이므로 ACTIVE 상태
     *
     * 검증 사항:
     * - ACTIVE 카운트 10명
     * - WAITING 카운트 0명
     */
    @Test
    @DisplayName("10명 대기열 진입 - 모두 ACTIVE 상태")
    @Transactional
    void enterQueue_TenUsers_AllActive() {
        // given & when
        for (int i = 0; i < 10; i++) {
            User user = createAndSaveTestUser("user" + i);
            queueService.enterQueue(user.getUserId());
        }

        // then
        assertThat(queueRepository.countByStatus(QueueStatus.ACTIVE)).isEqualTo(10);
        assertThat(queueRepository.countByStatus(QueueStatus.WAITING)).isEqualTo(0);
    }

    /**
     * 테스트 3: 100명 진입 - 50명 ACTIVE, 50명 WAITING
     *
     * 시나리오:
     * 1. 100명의 사용자가 순차적으로 대기열 진입
     * 2. 처음 50명은 ACTIVE, 나머지 50명은 WAITING
     *
     * 검증 사항:
     * - ACTIVE 카운트 50명
     * - WAITING 카운트 50명
     * - QueuePolicy의 MAX_ACTIVE_USERS(50) 정책 준수
     */
    @Test
    @DisplayName("100명 대기열 진입 - 50명 ACTIVE, 50명 WAITING")
    @Transactional
    void enterQueue_HundredUsers_FiftyActiveFiftyWaiting() {
        // given & when
        for (int i = 0; i < 100; i++) {
            User user = createAndSaveTestUser("hundred" + i);
            queueService.enterQueue(user.getUserId());
        }

        // then
        assertThat(queueRepository.countByStatus(QueueStatus.ACTIVE)).isEqualTo(50);
        assertThat(queueRepository.countByStatus(QueueStatus.WAITING)).isEqualTo(50);
    }

    /**
     * 테스트 4: 재진입 시 기존 세션 삭제 및 맨 뒤로 이동
     *
     * 시나리오:
     * 1. 50명 ACTIVE 채움
     * 2. Target 유저 진입 → WAITING 1번
     * 3. 2명 더 진입 → WAITING 3명
     * 4. Target 유저 재진입 → 기존 세션 삭제 후 새로운 세션 생성 (맨 뒤)
     *
     * 검증 사항:
     * - 첫 번째 토큰과 두 번째 토큰이 다름
     * - WAITING 카운트 여전히 3명 (기존 삭제 + 새로 추가)
     * - Target 유저의 순번이 3번 (맨 뒤)
     */
    @Test
    @DisplayName("재진입 시 기존 대기열 세션 삭제 및 맨 뒤로 이동")
    @Transactional
    void reenter_DeletesExistingSession_AndMovesToBack() {
        // given
        // 1. 50명 ACTIVE 채움
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("dummy" + i);
            queueService.enterQueue(user.getUserId());
        }

        // 2. Target 유저 진입 → WAITING 1번
        User targetUser = createAndSaveTestUser("target");
        QueueEnterResponse firstEntry = queueService.enterQueue(targetUser.getUserId());
        assertThat(firstEntry.getStatus()).isEqualTo(QueueStatus.WAITING);

        // 3. 2명 더 진입 → WAITING 증가
        User waiter1 = createAndSaveTestUser("waiter1");
        queueService.enterQueue(waiter1.getUserId());
        User waiter2 = createAndSaveTestUser("waiter2");
        queueService.enterQueue(waiter2.getUserId());

        assertThat(queueRepository.countByStatus(QueueStatus.WAITING)).isEqualTo(3);

        // when
        // 4. Target 유저 재진입
        QueueEnterResponse secondEntry = queueService.enterQueue(targetUser.getUserId());

        // then
        // 첫 번째 토큰과 두 번째 토큰이 달라야 함
        assertThat(secondEntry.getToken()).isNotEqualTo(firstEntry.getToken());

        // WAITING 수는 여전히 3명 (기존 삭제 후 추가)
        assertThat(queueRepository.countByStatus(QueueStatus.WAITING)).isEqualTo(3);

        // 맨 뒤인지 확인 (순번 3)
        QueueStatusResponse status = queueService.getQueueStatus(secondEntry.getToken());
        assertThat(status.getPosition()).isEqualTo(3);
    }

    /**
     * 테스트 5: 결제 진입(만료) 시 대기자 자동 활성화
     *
     * 시나리오:
     * 1. 50명 ACTIVE 채움
     * 2. 1명 WAITING 추가
     * 3. ACTIVE 유저 중 1명이 결제 진입 (expireQueue)
     * 4. WAITING 유저가 자동으로 ACTIVE로 전환
     *
     * 검증 사항:
     * - 결제 진입한 유저는 EXPIRED 상태
     * - WAITING 유저가 ACTIVE로 전환
     * - ACTIVE 카운트 여전히 50명
     */
    @Test
    @DisplayName("결제 진입(만료) 시 대기자 즉시 활성화")
    @Transactional
    void expireQueue_ActivatesNextWaitingUser() {
        // given
        // 1. 50명 ACTIVE
        List<User> activeUsers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("active" + i);
            queueService.enterQueue(user.getUserId());
            activeUsers.add(user);
        }

        // 2. 1명 WAITING
        User waiter = createAndSaveTestUser("waiter");
        QueueEnterResponse waiterResponse = queueService.enterQueue(waiter.getUserId());
        assertThat(waiterResponse.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(queueService.getQueueStatus(waiterResponse.getToken()).getPosition()).isEqualTo(1);

        // when
        // 3. ACTIVE 유저 중 한 명이 결제 진입 (expireQueue)
        Queue activeQueue = queueRepository.findByUserAndStatusIn(activeUsers.get(0), List.of(QueueStatus.ACTIVE))
                .orElseThrow();
        queueService.expireQueue(activeQueue.getTokenValue());

        // then
        // 4. 해당 ACTIVE 유저는 EXPIRED
        Queue expiredQueue = queueRepository.findById(activeQueue.getId()).orElseThrow();
        assertThat(expiredQueue.getStatus()).isEqualTo(QueueStatus.EXPIRED);

        // 5. WAITING 유저는 ACTIVE로 전환
        QueueStatusResponse waiterStatus = queueService.getQueueStatus(waiterResponse.getToken());
        assertThat(waiterStatus.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(waiterStatus.getPosition()).isEqualTo(0);
    }

    /**
     * 테스트 6: 퇴장 시 대기자 자동 활성화
     *
     * 시나리오:
     * 1. 50명 ACTIVE 채움
     * 2. 1명 WAITING 추가
     * 3. ACTIVE 유저 중 1명이 퇴장 (exitQueue)
     * 4. WAITING 유저가 자동으로 ACTIVE로 전환
     *
     * 검증 사항:
     * - 퇴장한 유저는 DB에서 삭제
     * - WAITING 유저가 ACTIVE로 전환
     * - ACTIVE 카운트 여전히 50명
     */
    @Test
    @DisplayName("퇴장 시 대기자 즉시 활성화")
    @Transactional
    void exitQueue_ActivatesNextWaitingUser() {
        // given
        // 1. 50명 ACTIVE
        List<User> activeUsers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("activeExit" + i);
            queueService.enterQueue(user.getUserId());
            activeUsers.add(user);
        }

        // 2. 1명 WAITING
        User waiter = createAndSaveTestUser("waiterExit");
        QueueEnterResponse waiterResponse = queueService.enterQueue(waiter.getUserId());
        assertThat(waiterResponse.getStatus()).isEqualTo(QueueStatus.WAITING);

        // when
        // 3. ACTIVE 유저 중 한 명이 퇴장
        Queue activeQueue = queueRepository.findByUserAndStatusIn(activeUsers.get(0), List.of(QueueStatus.ACTIVE))
                .orElseThrow();
        String exitToken = activeQueue.getTokenValue();
        queueService.exitQueue(exitToken);

        // then
        // 4. 해당 ACTIVE 유저는 삭제됨
        assertThat(queueRepository.findByTokenValue(exitToken)).isEmpty();

        // 5. WAITING 유저는 ACTIVE로 전환
        QueueStatusResponse waiterStatus = queueService.getQueueStatus(waiterResponse.getToken());
        assertThat(waiterStatus.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(waiterStatus.getPosition()).isEqualTo(0);
    }

    /**
     * 테스트 7: 동일 사용자의 중복 대기열 세션 방지
     *
     * 시나리오:
     * 1. 사용자 A가 대기열 진입 (첫 번째 세션)
     * 2. 사용자 A가 다시 대기열 진입 (재진입)
     * 3. 첫 번째 세션은 삭제되고 두 번째 세션만 존재
     *
     * 검증 사항:
     * - 사용자의 Queue가 DB에 1개만 존재
     * - 첫 번째 토큰으로 조회 불가
     * - 두 번째 토큰으로만 조회 가능
     */
    @Test
    @DisplayName("동일 사용자의 중복 대기열 세션이 생성되지 않음")
    @Transactional
    void enterQueue_SameUser_NoDuplicateSessions() {
        // given
        User user = createAndSaveTestUser("duplicateTest");

        // when
        // 첫 번째 진입
        QueueEnterResponse firstEntry = queueService.enterQueue(user.getUserId());
        String firstToken = firstEntry.getToken();

        // 두 번째 진입 (재진입)
        QueueEnterResponse secondEntry = queueService.enterQueue(user.getUserId());
        String secondToken = secondEntry.getToken();

        // then
        // 1. 두 토큰은 달라야 함
        assertThat(secondToken).isNotEqualTo(firstToken);

        // 2. 첫 번째 토큰으로 조회 불가 (이미 삭제됨)
        assertThat(queueRepository.findByTokenValue(firstToken)).isEmpty();

        // 3. 두 번째 토큰으로만 조회 가능
        assertThat(queueRepository.findByTokenValue(secondToken)).isPresent();

        // 4. 해당 사용자의 Queue가 1개만 존재
        List<Queue> userQueues = queueRepository.findAll().stream()
                .filter(q -> q.getUser().getUserId().equals(user.getUserId()))
                .toList();
        assertThat(userQueues).hasSize(1);
        assertThat(userQueues.get(0).getTokenValue()).isEqualTo(secondToken);
    }

    /**
     * 테스트 8: activateNextInQueue() 수동 호출 검증
     *
     * 시나리오:
     * 1. 50명 ACTIVE 채움
     * 2. 5명 WAITING 추가
     * 3. ACTIVE 유저 3명 수동 삭제 (DB에서 직접 삭제)
     * 4. activateNextInQueue() 수동 호출
     * 5. 3명의 WAITING 유저가 ACTIVE로 전환
     *
     * 검증 사항:
     * - ACTIVE 카운트 50명 (47 + 3)
     * - WAITING 카운트 2명 (5 - 3)
     */
    @Test
    @DisplayName("activateNextInQueue() - 빈자리만큼 대기자 활성화")
    @Transactional
    void activateNextInQueue_ActivatesAvailableSlots() {
        // given
        // 1. 50명 ACTIVE
        List<User> activeUsers = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("actManual" + i);
            queueService.enterQueue(user.getUserId());
            activeUsers.add(user);
        }

        // 2. 5명 WAITING
        for (int i = 0; i < 5; i++) {
            User user = createAndSaveTestUser("waitManual" + i);
            queueService.enterQueue(user.getUserId());
        }

        assertThat(queueRepository.countByStatus(QueueStatus.ACTIVE)).isEqualTo(50);
        assertThat(queueRepository.countByStatus(QueueStatus.WAITING)).isEqualTo(5);

        // 3. ACTIVE 유저 3명 수동 삭제
        for (int i = 0; i < 3; i++) {
            Queue queue = queueRepository.findByUserAndStatusIn(activeUsers.get(i), List.of(QueueStatus.ACTIVE))
                    .orElseThrow();
            queueRepository.delete(queue);
        }

        assertThat(queueRepository.countByStatus(QueueStatus.ACTIVE)).isEqualTo(47);

        // when
        // 4. activateNextInQueue() 수동 호출
        queueService.activateNextInQueue();

        // then
        // 5. 3명의 WAITING 유저가 ACTIVE로 전환
        assertThat(queueRepository.countByStatus(QueueStatus.ACTIVE)).isEqualTo(50);
        assertThat(queueRepository.countByStatus(QueueStatus.WAITING)).isEqualTo(2);
    }

    /**
     * 테스트 9: WAITING 순번 계산 검증
     *
     * 시나리오:
     * 1. 50명 ACTIVE 채움
     * 2. 10명 WAITING 추가
     * 3. 각 WAITING 유저의 순번이 1~10인지 검증
     *
     * 검증 사항:
     * - 첫 번째 WAITING 유저의 순번 1
     * - 10번째 WAITING 유저의 순번 10
     * - 순번이 진입 시간 순서대로 부여됨
     */
    @Test
    @DisplayName("WAITING 순번이 진입 시간 순서대로 계산됨")
    @Transactional
    void getQueueStatus_WaitingPosition_CalculatedCorrectly() {
        // given
        // 1. 50명 ACTIVE 채움
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("actPos" + i);
            queueService.enterQueue(user.getUserId());
        }

        // 2. 10명 WAITING 추가 (토큰 저장)
        List<String> waitingTokens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = createAndSaveTestUser("waitPos" + i);
            QueueEnterResponse response = queueService.enterQueue(user.getUserId());
            waitingTokens.add(response.getToken());
        }

        // when & then
        // 3. 각 WAITING 유저의 순번 검증
        for (int i = 0; i < 10; i++) {
            QueueStatusResponse status = queueService.getQueueStatus(waitingTokens.get(i));
            assertThat(status.getPosition()).isEqualTo(i + 1);
        }
    }

    /**
     * 테스트 10: ACTIVE 상태 유저의 순번은 항상 0
     *
     * 시나리오:
     * 1. 10명 ACTIVE 진입
     * 2. 각 유저의 순번이 0인지 검증
     *
     * 검증 사항:
     * - 모든 ACTIVE 유저의 position이 0
     */
    @Test
    @DisplayName("ACTIVE 상태 유저의 순번은 항상 0")
    @Transactional
    void getQueueStatus_ActivePosition_AlwaysZero() {
        // given
        List<String> activeTokens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User user = createAndSaveTestUser("actZero" + i);
            QueueEnterResponse response = queueService.enterQueue(user.getUserId());
            activeTokens.add(response.getToken());
        }

        // when & then
        for (String token : activeTokens) {
            QueueStatusResponse status = queueService.getQueueStatus(token);
            assertThat(status.getStatus()).isEqualTo(QueueStatus.ACTIVE);
            assertThat(status.getPosition()).isEqualTo(0);
        }
    }
}
