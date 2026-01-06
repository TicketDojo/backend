package com.ticket.dojo.backdeepfamily.domain.user.concurrency;

import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class UserRaceConditionTest {

    @Autowired
    @Qualifier("UserServiceImpl")
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Race Condition: 같은 이메일로 동시 회원가입 시 중복 생성")
    void 동시_회원가입_시_이메일_중복() throws InterruptedException {
        // given: 같은 이메일로 10개 스레드가 동시 가입 시도
        String duplicateEmail = "duplicate@test.com";
        String password = "password123";

        int concurrentAttempts = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentAttempts);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentAttempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 동시 회원가입 요청
        for (int i = 0; i < concurrentAttempts; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    UserLoginRequest request = new UserLoginRequest(duplicateEmail, password);
                    userService.join(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("회원가입 실패: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 동시 시작
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 해당 이메일 사용자는 1명만 존재해야 함
        List<User> usersWithEmail = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(duplicateEmail))
                .toList();

        System.out.println("====Race Condition 테스트 결과 (동시 회원가입)====");
        System.out.println("동시 가입 시도: " + concurrentAttempts);
        System.out.println("성공 횟수 (return 기준): " + successCount.get());
        System.out.println("생성된 사용자 수: " + usersWithEmail.size() + "\t기대: 1");

        // Race Condition 발생 시 동일 이메일로 여러 사용자 생성
        assertEquals(1, usersWithEmail.size(), 
                "같은 이메일로 1명만 가입되어야 합니다. (동시성 제어 필요)");
    }
}
