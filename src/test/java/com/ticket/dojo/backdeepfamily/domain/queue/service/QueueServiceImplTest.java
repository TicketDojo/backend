package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QueueServiceImplTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QueueRepository queueRepository;

    @BeforeEach
    void setUp() {
        // DB 초기화
        queueRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. 1인 대기열 입장 -> 즉시 Active")
    @Transactional
    void enterQueue_One() {
        // given
        User user = createAndSaveUser("u1");

        // when
        QueueEnterResponse response = queueService.enterQueue(user.getUserId());

        // then
        assertEquals(QueueStatus.ACTIVE, response.getStatus()); // 50명 미만이므로 Active
        assertEquals(1, queueRepository.countByStatus(QueueStatus.ACTIVE));
    }

    @Test
    @DisplayName("2. 10인 대기열 입장 -> 모두 Active")
    @Transactional
    void enterQueue_Ten() {
        // given
        for (int i = 0; i < 10; i++) {
            User user = createAndSaveUser("u" + i);
            queueService.enterQueue(user.getUserId());
        }

        // then
        assertEquals(10, queueRepository.countByStatus(QueueStatus.ACTIVE));
        assertEquals(0, queueRepository.countByStatus(QueueStatus.WAITING));
    }

    @Test
    @DisplayName("3. 100명 대기열 입장 -> 50명 Active, 50명 Waiting")
    @Transactional
    void enterQueue_Hundred() {
        // given
        for (int i = 0; i < 100; i++) {
            User user = createAndSaveUser("u_hun_" + i);
            queueService.enterQueue(user.getUserId());
        }

        // then
        assertEquals(50, queueRepository.countByStatus(QueueStatus.ACTIVE));
        assertEquals(50, queueRepository.countByStatus(QueueStatus.WAITING));
    }

    @Test
    @DisplayName("4. 재진입 시 기존 대기열 삭제 및 맨 뒤로 이동")
    @Transactional
    void reEntry_Reset() {
        // given
        // 1. 50명 Active 채움
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveUser("dummy" + i);
            queueService.enterQueue(user.getUserId());
        }

        // 2. Target 유저 입장 -> Waiting 1번
        User targetUser = createAndSaveUser("target");
        QueueEnterResponse firstEntry = queueService.enterQueue(targetUser.getUserId());
        assertEquals(QueueStatus.WAITING, firstEntry.getStatus());

        // 3. 몇 명 더 입장 -> Waiting 증가
        User dummyW1 = createAndSaveUser("dummy_w1");
        queueService.enterQueue(dummyW1.getUserId());
        User dummyW2 = createAndSaveUser("dummy_w2");
        queueService.enterQueue(dummyW2.getUserId());

        assertEquals(3, queueRepository.countByStatus(QueueStatus.WAITING));

        // when
        // 4. Target 유저 재진입
        QueueEnterResponse secondEntry = queueService.enterQueue(targetUser.getUserId());

        // then
        // 기존 토큰과 달라야 함
        assertNotEquals(firstEntry.getToken(), secondEntry.getToken());

        // 상태는 여전히 Waiting이지만, 맨 뒤로 갔으므로 순번이나 enteredAt이 바뀌었음
        // (Position은 동적 계산이므로 DB count로 확인)
        // 총 Waiting 수는 여전히 3명이어야 함 (기존 것 삭제 후 추가이므로)
        assertEquals(3, queueRepository.countByStatus(QueueStatus.WAITING));

        // 맨 뒤인지 확인 (Token으로 조회하여 상태 및 EnteredAt 비교 등)
        QueueStatusResponse status = queueService.getQueueStatus(secondEntry.getToken());

        // 순번이 3등이어야 함 (앞에 dummy_w1, dummy_w2가 있으므로)
        // 로직: dummy_w1(1), dummy_w2(2), target(3)
        assertEquals(3, status.getPosition());
    }

    @Test
    @DisplayName("5. 결제 진입(만료) 시 대기자 즉시 입장")
    @Transactional
    void payment_Entry_Activation() {
        // given
        // 1. 50명 Active
        List<User> activeUsers = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveUser("active" + i);
            queueService.enterQueue(user.getUserId());
            activeUsers.add(user);
        }

        // 2. 1명 Waiting
        User waiter = createAndSaveUser("waiter");
        QueueEnterResponse waiterResponse = queueService.enterQueue(waiter.getUserId());
        assertEquals(QueueStatus.WAITING, waiterResponse.getStatus());
        assertEquals(1, queueService.getQueueStatus(waiterResponse.getToken()).getPosition());

        // when
        // 3. Active 유저 중 한 명이 결제 진입 (expireQueue)
        // Active 유저의 토큰을 알아야 함. Repository에서 조회
        Queue activeQueue = queueRepository.findByUserAndStatusIn(activeUsers.get(0), List.of(QueueStatus.ACTIVE))
                .get();
        queueService.expireQueue(activeQueue.getTokenValue());

        // then
        // 4. 해당 Active 유저는 Expired
        Queue expiredQueue = queueRepository.findById(activeQueue.getId()).get();
        assertEquals(QueueStatus.EXPIRED, expiredQueue.getStatus());

        // 5. Waiting 유저는 Active로 변경되었어야 함
        QueueStatusResponse waiterStatus = queueService.getQueueStatus(waiterResponse.getToken());
        assertEquals(QueueStatus.ACTIVE, waiterStatus.getStatus());
        assertEquals(0, waiterStatus.getPosition());
    }

    @Test
    @DisplayName("6. 퇴장 시 대기자 즉시 입장")
    @Transactional
    void exit_Entry_Activation() {
        // given
        // 1. 50명 Active
        List<User> activeUsers = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveUser("active_exit" + i);
            queueService.enterQueue(user.getUserId());
            activeUsers.add(user);
        }

        // 2. 1명 Waiting
        User waiter = createAndSaveUser("waiter_exit");
        QueueEnterResponse waiterResponse = queueService.enterQueue(waiter.getUserId());
        assertEquals(QueueStatus.WAITING, waiterResponse.getStatus());

        // when
        // 3. Active 유저 중 한 명이 퇴장 (exitQueue)
        Queue activeQueue = queueRepository.findByUserAndStatusIn(activeUsers.get(0), List.of(QueueStatus.ACTIVE))
                .get();
        queueService.exitQueue(activeQueue.getTokenValue());

        // then
        // 4. 해당 Active 유저는 삭제됨
        assertTrue(queueRepository.findById(activeQueue.getId()).isEmpty());

        // 5. Waiting 유저는 Active로 변경되었어야 함
        QueueStatusResponse waiterStatus = queueService.getQueueStatus(waiterResponse.getToken());
        assertEquals(QueueStatus.ACTIVE, waiterStatus.getStatus());
    }

    private User createAndSaveUser(String suffix) {
        String email = "ts_" + suffix + "@test.com";
        String name = "test_" + suffix;
        User user = User.builder()
                .email(email)
                .password("pw")
                .name(name)
                .build();
        return userRepository.save(user);
    }
}