package com.ticket.dojo.backdeepfamily.domain.user.racecondition;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.domain.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("User Race Condition 테스트")
class UserRaceConditionTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Race Condition: 동일 이메일 동시 회원가입 시 중복 허용")
    void concurrentSignup() {
        // given
        String email = "race_test@test.com";
        String password = "password123";

        // when
        // 10명이 동시에 같은 이메일로 회원가입 시도
        int concurrentUsers = 10;
        ExecutorService executorService = Executors.newCachedThreadPool();
        Phaser phaser = new Phaser(concurrentUsers + 1);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentUsers; i++) {
            executorService.submit(() -> {
                try {
                    phaser.arriveAndAwaitAdvance();

                    UserLoginRequest request = new UserLoginRequest();
                    request.setEmail(email);
                    request.setPassword(password);

                    userService.join(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();

        // then
        int userCount = userRepository.findAll().size();

        System.err.println("=== Results ===");
        System.err.println("동시 시도 인원: " + concurrentUsers);
        System.err.println("성공 인원: " + successCount.get());
        System.err.println("실제 생성된 계정 수: " + userCount);

        // Race Condition 발생 시: 여러 계정 생성됨
        // 정상: 1개만 생성되어야 함
        assertThat(userCount).isEqualTo(1);
    }
}
