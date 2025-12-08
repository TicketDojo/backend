package com.ticket.dojo.backdeepfamily.domain.queue.scheduler;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
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
class QueueSchedulerTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private User testUser3;


    @BeforeEach
    void setUp(){
        // 테스트 용 User 생성
        testUser1 = createAndSaveUser("test1@naver.com", "테스트유저1");
        testUser2 = createAndSaveUser("test2@naver.com", "테스트유저2");
        testUser3 = createAndSaveUser("test3@naver.com", "테스트유저3");
    }

    @Test
    @DisplayName("Waiting -> Active 전환 테스트")
    @Transactional
    void activateNextInQueue(){
        // given
        queueService.enterQueue(testUser1.getUserId());
        queueService.enterQueue(testUser2.getUserId());
        queueService.enterQueue(testUser3.getUserId());

        // 모두 Waiting 상태 확인
        int waitCount = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
        assertEquals(3, waitCount);

        // when
        queueService.activateNextInQueue(10);

        // then
        int activeCount = queueRepository.countByStatus(Queue.QueueStatus.ACTIVE);
        assertEquals(3, activeCount);

        int waitCountAfter = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
        assertEquals(0, waitCountAfter);

        List<Queue> activeQueue = queueRepository.findAll();
        for(Queue queue : activeQueue){
            assertNotNull(queue.getActivatedAt());
            assertNotNull(queue.getExpiresAt());
            assertEquals(queue.getStatus(), Queue.QueueStatus.ACTIVE);
        }
    }

    @Test
    @DisplayName("대기자가 없을 때 활성화 시도")
    @Transactional
    void nullWaitingQueue(){
        // given
        int waitCount = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
        assertEquals(0, waitCount);

        // when
        queueService.activateNextInQueue(10);

        // then
        int activeCount = queueRepository.countByStatus(Queue.QueueStatus.ACTIVE);
        assertEquals(0, activeCount);
    }

    @Test
    @DisplayName("만료된 Queue 정리 테스트")
    @Transactional
    void cleanupExpiredQueue(){

    }

    @Test
    @DisplayName("10명 초과 대기 시 10명만 활성화")
    @Transactional
    void activeNextQueueLimit10(){
        // given
        for(int i=1; i<=20; i++){
            User user = createAndSaveUser("test" + i + "@naver.com", "테스트유저" + i);
            queueService.enterQueue(user.getUserId());
        }

        int waitCount = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
        assertEquals(20, waitCount);

        // when
        queueService.activateNextInQueue(10);

        // then
        int waitCountAfter = queueRepository.countByStatus(Queue.QueueStatus.WAITING);
        int activeCountAfter = queueRepository.countByStatus(Queue.QueueStatus.ACTIVE);

        assertEquals(10, waitCountAfter);
        assertEquals(10, activeCountAfter);
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