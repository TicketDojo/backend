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
@Transactional
public class QueueExistTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private User testUser3;
    private User testUser4;

    @BeforeEach
    void setUp() {
        testUser1 = createAndSaveUser("user1@test.com", "테스트유저1");
        testUser2 = createAndSaveUser("user2@test.com", "테스트유저2");
        testUser3 = createAndSaveUser("user3@test.com", "테스트유저3");
        testUser4 = createAndSaveUser("user4@test.com", "테스트유저4");
    }

    @Test
    @DisplayName("대기열 퇴장")
    void exitQueue(){
        // given
        QueueEnterResponse response = queueService.enterQueue(testUser1.getUserId());
        String token = response.getToken();

        // when
        queueService.exitQueue(token);

        // then
        assertFalse(queueRepository.findByToken(token).isPresent());
    }

    @Test
    @DisplayName("존재하지 않는 토큰으로 퇴장시 예외 발생")
    void exitQueueThrowException(){
        // given
        String token = "123";

        assertThrows(QueueNotFoundException.class, () -> {
            queueService.exitQueue(token);
        });
    }

    @Test
    @DisplayName("앞사람 퇴장 시 뒷사람 순번 자동 변경")
    void exitQueuePositionUpdateAll(){
        // given
        QueueEnterResponse response1 = queueService.enterQueue(testUser1.getUserId());
        QueueEnterResponse response2 = queueService.enterQueue(testUser2.getUserId());
        QueueEnterResponse response3 = queueService.enterQueue(testUser3.getUserId());

        // 초기 순번 확인
        assertEquals(1, response1.getPosition());
        assertEquals(2, response2.getPosition());
        assertEquals(3, response3.getPosition());

        // when
        queueService.exitQueue(response1.getToken());

        // then
        QueueStatusResponse queueStatus2 = queueService.getQueueStatus(response2.getToken());
        QueueStatusResponse queueStatus3 = queueService.getQueueStatus(response3.getToken());

        assertEquals(1, queueStatus2.getPosition());
        assertEquals(2, queueStatus3.getPosition());
    }

    @Test
    @DisplayName("중간사람 퇴장 시 뒷사람 순번 자동 변경")
    void exitQueuePositionUpdateMiddle(){
        // given
        QueueEnterResponse response1 = queueService.enterQueue(testUser1.getUserId());
        QueueEnterResponse response2 = queueService.enterQueue(testUser2.getUserId());
        QueueEnterResponse response3 = queueService.enterQueue(testUser3.getUserId());
        QueueEnterResponse response4 = queueService.enterQueue(testUser4.getUserId());

        // when
        queueService.exitQueue(response2.getToken());

        // then
        QueueStatusResponse status1 = queueService.getQueueStatus(response1.getToken());
        QueueStatusResponse status3 = queueService.getQueueStatus(response3.getToken());
        QueueStatusResponse status4 = queueService.getQueueStatus(response4.getToken());

        assertEquals(1, status1.getPosition());
        assertEquals(2, status3.getPosition());
        assertEquals(3, status4.getPosition());
    }

    @Test
    @DisplayName("전체 플로우 : 진입 -> 조회 -> 퇴장")
    void fullFlow(){
        // 1. 진입
        QueueEnterResponse response = queueService.enterQueue(testUser1.getUserId());
        assertEquals(1, response.getPosition());

        // 2. 상태 조회
        QueueStatusResponse status = queueService.getQueueStatus(response.getToken());
        assertEquals(1, status.getPosition());
        assertEquals(Queue.QueueStatus.WAITING, status.getStatus());

        // 3. 퇴장
        queueService.exitQueue(status.getToken());

        // 4. 퇴장 후 조회 시 예외 발생
        assertThrows(QueueNotFoundException.class, () ->{
           queueService.getQueueStatus(response.getToken());
        });
    }

    private User createAndSaveUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .password("qwe123")
                .name(name)
                .build();

        return userRepository.save(user);
    }
}
