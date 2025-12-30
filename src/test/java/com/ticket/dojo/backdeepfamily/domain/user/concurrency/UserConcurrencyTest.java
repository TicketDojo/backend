package com.ticket.dojo.backdeepfamily.domain.user.concurrency;

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

import com.ticket.dojo.backdeepfamily.domain.user.service.UserService;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
public class UserConcurrencyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        userRepository.deleteAll();
        log.info("▶▶▶ 기존 데이터 정리 완료");
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리
        userRepository.deleteAll();
        log.info("▶▶▶ 테스트 데이터 정리 완료");
    }

    @Test
    @DisplayName("동일한 이메일로 10번 동시에 회원가입을 시도할 때, 1개의 User만 생성되어야 한다.")
    void testDuplicateEmailRegistrationRace() throws InterruptedException {
        String testEmail = "duplicate@test.com";
        String testPassword = "password123";

        // 10개의 스레드가 동시에 동일한 이메일로 회원가입 시도
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<String> errors = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 {}] 회원가입 시도: {}", threadNum, testEmail);

                    UserLoginRequest request = new UserLoginRequest();
                    request.setEmail(testEmail);
                    request.setPassword(testPassword);

                    userService.join(request);

                    log.info("[스레드 {}] 회원가입 완료", threadNum);
                } catch (InterruptedException e) {
                    log.error("[스레드 {} 인터럽트] {}", threadNum, e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("[스레드 {} 에러] {}", threadNum, e.getMessage());
                    errors.add(e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 join() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 검증: 해당 이메일의 User는 1개만 생성되어야 함
        List<User> users = userRepository.findAll().stream()
            .filter(u -> u.getEmail().equals(testEmail))
            .toList();

        log.info("최종 생성된 User 개수: {}", users.size());
        log.info("발생한 에러들: {}", errors);

        assertThat(users)
            .as("동일 이메일로 10번 동시 회원가입 시도 시 User는 1개만 생성되어야 합니다")
            .hasSize(1);
    }

    @Test
    @DisplayName("서로 다른 이메일로 10번 동시에 회원가입을 시도할 때, 10개의 User가 생성되어야 한다.")
    void testConcurrentDifferentEmailRegistration() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<String> createdEmails = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final String email = "user" + i + "@test.com";
            final String password = "password" + i;
            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 시작] 회원가입 시도: {}", email);

                    UserLoginRequest request = new UserLoginRequest();
                    request.setEmail(email);
                    request.setPassword(password);

                    userService.join(request);
                    createdEmails.add(email);

                    log.info("[회원가입 성공] {}", email);
                } catch (InterruptedException e) {
                    log.error("[인터럽트 발생] {}", e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("[에러 발생] Email: {}, Error: {}", email, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 join() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 검증: 10개의 고유한 User가 생성되어야 함
        long userCount = userRepository.count();

        log.info("최종 생성된 User 개수: {}", userCount);
        log.info("생성된 이메일들: {}", createdEmails);

        assertThat(userCount)
            .as("서로 다른 이메일로 10번 동시 회원가입 시 10개의 User가 생성되어야 합니다")
            .isEqualTo(10);

        assertThat(createdEmails)
            .as("모든 이메일이 성공적으로 생성되어야 합니다")
            .hasSize(10);
    }

    @Test
    @DisplayName("동일한 이메일로 재가입 시도가 동시에 발생할 때, 기존 User를 유지해야 한다.")
    void testReRegistrationRace() throws InterruptedException {
        // 1. 사전에 User 생성
        String testEmail = "existing@test.com";
        UserLoginRequest initialRequest = new UserLoginRequest();
        initialRequest.setEmail(testEmail);
        initialRequest.setPassword("initial_password");
        userService.join(initialRequest);

        User existingUser = userRepository.findByEmail(testEmail);
        Long existingUserId = existingUser.getUserId();
        log.info("▶▶▶ 기존 User 생성 완료: userId={}", existingUserId);

        // 2. 5개의 스레드가 동시에 동일한 이메일로 재가입 시도
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 {}] 재가입 시도: {}", threadNum, testEmail);

                    UserLoginRequest request = new UserLoginRequest();
                    request.setEmail(testEmail);
                    request.setPassword("new_password_" + threadNum);

                    userService.join(request);

                    log.info("[스레드 {}] 재가입 시도 완료", threadNum);
                } catch (InterruptedException e) {
                    log.error("[스레드 {} 인터럽트] {}", threadNum, e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("[스레드 {} 에러] {}", threadNum, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 재가입 시도 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 3. 검증: 여전히 1개의 User만 존재하고, ID가 변경되지 않았는지 확인
        List<User> users = userRepository.findAll().stream()
            .filter(u -> u.getEmail().equals(testEmail))
            .toList();

        log.info("최종 User 개수: {}", users.size());

        assertThat(users)
            .as("재가입 시도 후에도 User는 1개만 존재해야 합니다")
            .hasSize(1);

        assertThat(users.get(0).getUserId())
            .as("기존 User의 ID가 유지되어야 합니다")
            .isEqualTo(existingUserId);
    }
}
