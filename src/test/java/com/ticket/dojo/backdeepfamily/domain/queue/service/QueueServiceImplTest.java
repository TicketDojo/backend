package com.ticket.dojo.backdeepfamily.domain.queue.service;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.QueueNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class QueueServiceImplTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QueueRepository queueRepository;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setTestUser(){
        // 테스트용 User 생성
        testUser1 = User.builder().email("test1@test.com").name("테스트유저1").password("qwe123").build();
        testUser2 = User.builder().email("test2@test.com").name("테스트유저2").password("qwe123").build();
        testUser3 = User.builder().email("test3@test.com").name("테스트유저3").password("qwe123").build();

        userRepository.save(testUser1);
        userRepository.save(testUser2);
        userRepository.save(testUser3);
    }

    @Test
    @DisplayName("1인 대기열 진입")
    @Transactional
    void enterQueue_One(){
        // when
        QueueEnterResponse response = queueService.enterQueue(testUser1.getUserId());

        // then
        assertNotNull(response.getToken());
        assertEquals(1, response.getPosition());
        assertEquals(Queue.QueueStatus.WAITING, response.getStatus());
        assertNotNull(response.getEnteredAt());
    }

    @Test
    @DisplayName("태기열 상태 조회 성공")
    @Transactional
    void getQueueStatusSuccess(){
        // given
        QueueEnterResponse enterResponse = queueService.enterQueue(testUser1.getUserId());
        String token = enterResponse.getToken();

        // when
        QueueStatusResponse statusResponse = queueService.getQueueStatus(token);

        // then
        assertEquals(token, statusResponse.getToken());
        assertEquals(1, statusResponse.getPosition());
        assertEquals(Queue.QueueStatus.WAITING, statusResponse.getStatus());
        assertEquals(enterResponse.getEnteredAt(), statusResponse.getEnteredAt());
        assertNull(statusResponse.getActivatedAt());
        assertNull(statusResponse.getExpiresAt());

    }

    @Test
    @DisplayName("여러명 진입 수 순번 확인")
    @Transactional
    void manyPeople_EnterQueue(){
        // given
        QueueEnterResponse response1 = queueService.enterQueue(testUser1.getUserId());
        QueueEnterResponse response2 = queueService.enterQueue(testUser2.getUserId());
        QueueEnterResponse response3 = queueService.enterQueue(testUser3.getUserId());

        // when
        QueueStatusResponse queueStatus1 = queueService.getQueueStatus(response1.getToken());
        QueueStatusResponse queueStatus2 = queueService.getQueueStatus(response2.getToken());
        QueueStatusResponse queueStatus3 = queueService.getQueueStatus(response3.getToken());

        // then
        assertEquals(1, queueStatus1.getPosition());
        assertEquals(2, queueStatus2.getPosition());
        assertEquals(3, queueStatus3.getPosition());
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 조회 시 예외 발생")
    void tokenNotFoundException(){
        // given
        String token = "123";

        // then
        assertThrows(QueueNotFoundException.class, () -> {
            queueService.getQueueStatus(token);
        });
    }

    @Test
    @DisplayName("대기열 진입 후 상태 조회 - 통합 시나리오")
    @Transactional
    void fullScenario(){
        // 1. 첫 번째 사용자 진입
        QueueEnterResponse enter1 = queueService.enterQueue(testUser1.getUserId());
        assertEquals(1, enter1.getPosition());

        // 2. 두 번째 사용자 진입
        QueueEnterResponse enter2 = queueService.enterQueue(testUser2.getUserId());
        assertEquals(2, enter2.getPosition());

        // 3. 첫 번째 사용자 상태 조회
        QueueStatusResponse status1 = queueService.getQueueStatus(enter1.getToken());
        assertEquals(1, status1.getPosition());
        assertEquals(Queue.QueueStatus.WAITING, status1.getStatus());

        // 4. 두 번째 사용자 상태 조회
        QueueStatusResponse status2 = queueService.getQueueStatus(enter2.getToken());
        assertEquals(2, status2.getPosition());
        assertEquals(Queue.QueueStatus.WAITING, status2.getStatus());
    }
}