package com.ticket.dojo.backdeepfamily.domain.queue.racecondition;

import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("대기열 Race Condition 테스트")
class QueueRaceConditionTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        queueRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Race Condition 1: 동시 진입 시 ACTIVE 인원 50명 초과")
    void concurrentEntry() {
        // given
        for (int i = 0; i < 49; i++) {
            User user = createAndSaveUser("active_" + i);
            queueService.enterQueue(user.getUserId());
        }

        int initialActiveCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        assertThat(initialActiveCount).isEqualTo(49);

        // when
        int concurrentUsers = 100;
        ExecutorService executorService = Executors.newCachedThreadPool();
        Phaser phaser = new Phaser(concurrentUsers + 1); // 메인 스레드 포함

        List<User> testUsers = new ArrayList<>();
        for (int i = 0; i < concurrentUsers; i++) {
            testUsers.add(createAndSaveUser("concurrent_" + i));
        }

        for (User user : testUsers) {
            executorService.submit(() -> {
                try {
                    // 모든 스레드가 준비될 때까지 대기
                    phaser.arriveAndAwaitAdvance();
                    queueService.enterQueue(user.getUserId());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        // 모든 스레드가 준비완료되면 동시에 시작
        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();

        // then
        int finalActiveCount = queueRepository.countByStatus(QueueStatus.ACTIVE);
        assertThat(finalActiveCount).isLessThanOrEqualTo(50);
    }

    /**
     * 성공할때도 있지만 실패할때도 있음
     * expected: 1000L
     * but was: 999L
     */
    @RepeatedTest(5)
    @DisplayName("Race Condition 2: 동시 진입시 WAITING 실시간 순번 중복 테스트")
    void concurrentWaitingEntry() {
        // given
        int activeUserCount = 50;
        for (int i = 0; i < activeUserCount; i++) {
            User user = createAndSaveUser("active_" + i);
            queueService.enterQueue(user.getUserId());
        }

        // when
        int concurrentWaiting = 1000;
        ExecutorService executorService = Executors.newCachedThreadPool();
        Phaser phaser = new Phaser(concurrentWaiting + 1);

        List<User> waitingUsers = new ArrayList<>();
        for (int i = 0; i < concurrentWaiting; i++) {
            waitingUsers.add(createAndSaveUser("waiting_" + i));
        }

        List<QueueEnterResponse> responses = new CopyOnWriteArrayList<>();

        for (User user : waitingUsers) {
            executorService.submit(() -> {
                try {
                    phaser.arriveAndAwaitAdvance();
                    QueueEnterResponse response = queueService.enterQueue(user.getUserId());
                    responses.add(response);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();

        List<Queue> waitingQueues = queueRepository.findByStatusOrderByEnteredAtAsc(
                QueueStatus.WAITING, null);

        List<Integer> positions = waitingQueues.stream()
                .map(q -> queueService.getQueueStatus(q.getTokenValue()).getPosition())
                .toList();
        long distinctPositions = positions.stream().distinct().count();

        // then
        assertThat(distinctPositions).isEqualTo(positions.size());
    }

    private User createAndSaveUser(String suffix) {
        String email = "race_" + suffix + "@test.com";
        User user = User.builder()
                .email(email)
                .password("password")
                .name(email)
                .role(User.Role.USER)
                .build();
        return userRepository.save(user);
    }
}
