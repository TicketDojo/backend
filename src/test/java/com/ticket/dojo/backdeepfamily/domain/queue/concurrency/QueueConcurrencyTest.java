package com.ticket.dojo.backdeepfamily.domain.queue.concurrency;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
public class QueueConcurrencyTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리 (테스트 격리 보장)
        queueRepository.deleteAll();
        userRepository.deleteAll();
        log.info("▶▶▶ 기존 데이터 정리 완료");
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리
        queueRepository.deleteAll();
        userRepository.deleteAll();
        log.info("▶▶▶ 테스트 데이터 정리 완료");
    }


    @Test
    @DisplayName("10개의 스레드가 동시에 진입할 때, 발급된 순번(Position)은 모두 중복이 없어야 한다.")
    void testPositionAssignmentRace() throws InterruptedException {
        // ACTIVE 슬롯을 먼저 50개 채워서 테스트 유저들이 WAITING 상태가 되도록 설정
        log.info("▶▶▶ ACTIVE 슬롯 50개 채우기 시작");
        for (int i = 0; i < 50; i++) {
            User dummyUser = userRepository.save(User.builder()
                .email("dummy" + i + "@test.com")
                .password("1234")
                .name("Dummy" + i)
                .build());
            queueService.enterQueue(dummyUser.getUserId());
        }
        log.info("▶▶▶ ACTIVE 슬롯 50개 채우기 완료");

        // 테스트에 사용할 10명의 유저 생성 (고유한 이메일 사용)
        List<Long> testUserIds = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            User testUser = userRepository.save(User.builder()
                .email("test" + i + "@test.com")
                .password("1234")
                .name("TestUser" + i)
                .build());
            testUserIds.add(testUser.getUserId());
        }
        log.info("▶▶▶ 테스트용 유저 10명 생성 완료: {}", testUserIds);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<Integer> positions = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final Long userId = testUserIds.get(i);  // 실제 저장된 User ID 사용
            executorService.submit(() -> {
                try {
                    latch.await(); // 대기
                    log.info("[스레드 시작] User ID: {}", userId);

                    var queueInfo = queueService.enterQueue(userId);
                    Integer position = queueInfo.getPosition();

                    log.info("[순번 할당 성공] User ID: {}, Position: {}", userId, position);
                    positions.add(position);
                } catch (InterruptedException e) {
                    log.error("[인터럽트 발생] User ID: {}", userId, e);
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // 이 로그가 찍히면 로직 상의 문제(DB 락, 제약조건 위반 등)를 바로 알 수 있습니다.
                    log.error("[비즈니스 로직 에러] User ID: {} - 에러 메시지: {}", userId, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 출발 신호 전송!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        log.info("최종 수집된 순번들: {}", positions);
        log.info("기대 결과 개수: {}, 실제 결과 개수: {}", threadCount, positions.size());

        assertThat(positions)
            .as("발급된 순번들(%s)의 개수는 %d개여야 합니다.", positions, threadCount)
            .hasSize(threadCount);
    }

    @Test
    @DisplayName("Scheduler와 exitQueue()가 동시에 activateNextInQueue()를 호출할 때, 정확히 1명만 활성화되어야 한다.")
    void testSchedulerVsUserRace() throws InterruptedException {
        // 1. ACTIVE 슬롯을 50개로 가득 채우기
        log.info("▶▶▶ ACTIVE 슬롯 50개 채우기 시작");
        List<String> activeTokens = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User activeUser = userRepository.save(User.builder()
                .email("active" + i + "@test.com")
                .password("1234")
                .name("ActiveUser" + i)
                .build());
            var response = queueService.enterQueue(activeUser.getUserId());
            activeTokens.add(response.getToken());
        }
        log.info("▶▶▶ ACTIVE 슬롯 50개 채우기 완료");

        // 2. WAITING 상태의 유저 1명 추가
        User waitingUser = userRepository.save(User.builder()
            .email("waiting@test.com")
            .password("1234")
            .name("WaitingUser")
            .build());
        queueService.enterQueue(waitingUser.getUserId());
        log.info("▶▶▶ WAITING 유저 1명 추가 완료");

        // 3. 2개의 스레드가 동시에 activateNextInQueue() 호출
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // 한 스레드는 exitQueue()를 통해, 다른 스레드는 직접 activateNextInQueue() 호출
        executorService.submit(() -> {
            try {
                latch.await();
                log.info("[스레드 1] exitQueue() 호출 (내부적으로 activateNextInQueue() 호출됨)");
                queueService.exitQueue(activeTokens.get(0));
            } catch (Exception e) {
                log.error("[스레드 1 에러] {}", e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                latch.await();
                log.info("[스레드 2] activateNextInQueue() 직접 호출 (Scheduler 시뮬레이션)");
                queueService.activateNextInQueue();
            } catch (Exception e) {
                log.error("[스레드 2 에러] {}", e.getMessage());
            } finally {
                doneLatch.countDown();
            }
        });

        log.info("▶▶▶ 동시 activateNextInQueue() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 4. 검증: ACTIVE 상태는 정확히 50개여야 함 (1명 퇴장, 1명 활성화)
        long activeCount = queueRepository.countByStatus(com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus.ACTIVE);
        log.info("최종 ACTIVE 개수: {}", activeCount);

        assertThat(activeCount)
            .as("동시 activateNextInQueue() 호출 후 ACTIVE는 정확히 50명이어야 합니다 (중복 활성화 없음)")
            .isEqualTo(50);
    }

    @Test
    @DisplayName("동일한 사용자가 5번 동시에 enterQueue()를 호출할 때, 1개의 Queue만 존재해야 한다.")
    void testEntryCleanupRace() throws InterruptedException {
        // 1. 테스트용 유저 1명 생성
        User testUser = userRepository.save(User.builder()
            .email("duplicate@test.com")
            .password("1234")
            .name("DuplicateTestUser")
            .build());
        log.info("▶▶▶ 테스트 유저 생성 완료: userId={}", testUser.getUserId());

        // 2. 동일한 userId로 5번 동시에 enterQueue() 호출
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<String> tokens = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final int attemptNum = i + 1;
            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 {}] enterQueue() 호출 시작", attemptNum);

                    var response = queueService.enterQueue(testUser.getUserId());
                    tokens.add(response.getToken());

                    log.info("[스레드 {}] enterQueue() 성공 - Token: {}", attemptNum, response.getToken());
                } catch (InterruptedException e) {
                    log.error("[스레드 {} 인터럽트] {}", attemptNum, e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("[스레드 {} 에러] {}", attemptNum, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 enterQueue() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 3. 검증: 해당 유저의 Queue는 정확히 1개만 존재해야 함
        List<com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue> userQueues =
            queueRepository.findAll().stream()
                .filter(q -> q.getUser().getUserId().equals(testUser.getUserId()))
                .toList();

        log.info("최종 생성된 토큰 개수: {}", tokens.size());
        log.info("최종 DB에 저장된 Queue 개수: {}", userQueues.size());
        log.info("생성된 토큰들: {}", tokens);

        assertThat(userQueues)
            .as("동일 유저가 5번 동시 진입 시도 시 Queue는 1개만 존재해야 합니다 (중복 생성 방지)")
            .hasSize(1);

        assertThat(tokens)
            .as("발급된 토큰도 1개만 존재해야 합니다")
            .hasSize(1);
    }
}
