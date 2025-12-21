package com.ticket.dojo.backdeepfamily.domain.queue.concurrency;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
public class raceConditionTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp(){
        queueRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Race Condition : 동시 입장 시 50명 제한 초과")
    void 동시입장_시_입장인원_50명_초과() throws InterruptedException {
        // given : 49명을 먼저 입장 (49명 Active)
        for(int i=0; i<49; i++){
            User user = createAndSaveUser("test" + i);
            queueService.enterQueue(user.getUserId());
        }

        assertEquals(49, queueRepository.countByStatus(Queue.QueueStatus.ACTIVE));

        // when : 10명이 동시에 진입
        int concurrentUsers = 10;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentUsers);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);

        List<User> concurrentUserList = new ArrayList<>();
        for(int i=0; i < concurrentUsers; i++){
            User user = createAndSaveUser("concurrent" + i);
            concurrentUserList.add(user);
        }

        for(User user : concurrentUserList){
            executorService.submit(() -> {
               try{
                   startLatch.await(); // 모든 스레드가 동시에 시작하도록 대기
                   queueService.enterQueue(user.getUserId());
               } catch (Exception e){
                   e.printStackTrace();
               } finally {
                   doneLatch.countDown();
               }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        doneLatch.await(10, TimeUnit.SECONDS); // 모든 스레드 완료 대기
        executorService.shutdown();

        // then : 50명만 Active여야 함 (Race Condition 발생 시 50명 초과)
        int activeCount = queueRepository.countByStatus(Queue.QueueStatus.ACTIVE);
        int waitingCount = queueRepository.countByStatus(Queue.QueueStatus.WAITING);

        System.out.println("=====Race Condition 테스트 결과=====");
        System.out.println("실제 접속 인원 : " + activeCount + "\t 기대 접속 인원 : 50" );
        System.out.println("실제 대기 인원 : " + waitingCount + "\t 기대 대기 인원 : 9");
        System.out.println("전체     인원 : " + (activeCount + waitingCount));

        // Race Condition이 발생하면 테스트 실패
        assertNotEquals(50, activeCount);
        assertNotEquals(50, waitingCount);
    }

    @Test
    @DisplayName("Race Condition : 동시 재진입 시 중복 대기열 생성")
    void 동시_재진입_시_중복_대기열() throws InterruptedException {
        // given: 한 명의 사용자 생성
        User targetUser = createAndSaveUser("target_user");

        // when: 같은 사용자가 동시에 5번 입장 시도
        int concurrentAttempts = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentAttempts);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentAttempts);

        List<QueueEnterResponse> responses = new ArrayList<>();

        for (int i = 0; i < concurrentAttempts; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 시작
                    QueueEnterResponse response = queueService.enterQueue(targetUser.getUserId());
                    synchronized (responses) {
                        responses.add(response);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        doneLatch.await(10, TimeUnit.SECONDS); // 완료 대기
        executorService.shutdown();

        // then: 해당 사용자의 대기열은 1개만 존재해야 함
        List<Queue> userQueues = queueRepository.findAll().stream()
                .filter(q -> q.getUser().getUserId().equals(targetUser.getUserId()))
                .collect(Collectors.toList());

        System.out.println("=== Race Condition Test Result ===");
        System.out.println("동시 진입 시도 횟수: " + concurrentAttempts);
        System.out.println("생성된 응답 수: " + responses.size());
        System.out.println("저장된 대기열 수: " + userQueues.size() + "\t기대 대기열 수 : 1");
        System.out.println("생성된 토큰들:");
        responses.forEach(r -> System.out.println("  - " + r.getToken()));

        assertNotEquals(1, userQueues.size());

        // 모든 응답의 토큰이 동일해야 함 (마지막 진입한 것만 유효)
        Set<String> uniqueTokens = responses.stream()
                .map(QueueEnterResponse::getToken)
                .collect(Collectors.toSet());

        System.out.println("고유 토큰 수: " + uniqueTokens.size());
    }

    private User createAndSaveUser(String s) {
        User user = User.builder().email(s + "@naver.com").password("qweqwe").name(s).build();
        return userRepository.save(user);
    }

}
