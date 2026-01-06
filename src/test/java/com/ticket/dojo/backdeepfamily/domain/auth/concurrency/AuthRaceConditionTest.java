package com.ticket.dojo.backdeepfamily.domain.auth.concurrency;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.RefreshToken;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;
import com.ticket.dojo.backdeepfamily.domain.auth.service.AuthService;
import com.ticket.dojo.backdeepfamily.domain.auth.service.RefreshService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class AuthRaceConditionTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshService refreshService;

    @Autowired
    private RefreshRepository refreshRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        refreshRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Race Condition: 같은 사용자가 동시 로그인 시 Refresh Token 중복 생성")
    void 동시_로그인_시_리프레시토큰_중복() throws InterruptedException {
        // given: 1명의 사용자 생성
        User user = createAndSaveUser("testuser");
        String email = user.getEmail();

        // when: 10개 스레드가 동시에 Refresh Token 저장 (로그인 시뮬레이션)
        int concurrentLogins = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentLogins);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentLogins);

        List<String> savedTokens = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentLogins; i++) {
            final int loginIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    String refreshToken = UUID.randomUUID().toString();
                    LocalDateTime expiration = LocalDateTime.now().plusDays(1);
                    
                    refreshService.saveRefreshToken(email, refreshToken, expiration);
                    
                    synchronized (savedTokens) {
                        savedTokens.add(refreshToken);
                    }
                    successCount.incrementAndGet();
                    System.out.println("로그인 " + loginIndex + " 성공 - Token: " + refreshToken.substring(0, 8) + "...");
                } catch (Exception e) {
                    System.out.println("로그인 " + loginIndex + " 실패: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 해당 사용자의 Refresh Token은 1개만 존재해야 함 (1유저 1토큰 정책)
        List<RefreshToken> tokensInDb = refreshRepository.findAll().stream()
                .filter(t -> t.getEmail().equals(email))
                .toList();

        System.out.println("====Race Condition 테스트 결과 (동시 로그인)====");
        System.out.println("동시 로그인 시도: " + concurrentLogins);
        System.out.println("성공 횟수: " + successCount.get());
        System.out.println("저장된 토큰 수: " + tokensInDb.size() + "\t기대: 1");

        assertEquals(1, tokensInDb.size(), 
                "같은 사용자의 Refresh Token은 1개만 존재해야 합니다. (동시성 제어 필요)");
    }

    @Test
    @DisplayName("Race Condition: 같은 Refresh Token으로 동시 재발급 요청")
    void 동시_토큰_재발급_시_충돌() throws InterruptedException {
        // given: 사용자와 Refresh Token 생성
        User user = createAndSaveUser("testuser");
        String originalRefreshToken = UUID.randomUUID().toString();
        LocalDateTime expiration = LocalDateTime.now().plusDays(1);
        refreshService.saveRefreshToken(user.getEmail(), originalRefreshToken, expiration);

        // when: 10개 스레드가 동시에 같은 Refresh Token으로 재발급 요청
        int concurrentRequests = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequests);

        List<AuthService.TokenPair> successfulPairs = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentRequests; i++) {
            final int requestIndex = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    AuthService.TokenPair tokenPair = authService.reissueTokens(originalRefreshToken);
                    synchronized (successfulPairs) {
                        successfulPairs.add(tokenPair);
                    }
                    successCount.incrementAndGet();
                    System.out.println("재발급 " + requestIndex + " 성공");
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    System.out.println("재발급 " + requestIndex + " 실패: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 결과 분석
        System.out.println("====Race Condition 테스트 결과 (동시 토큰 재발급)====");
        System.out.println("동시 재발급 시도: " + concurrentRequests);
        System.out.println("성공 횟수: " + successCount.get() + "\t기대: 1");
        System.out.println("실패 횟수: " + failCount.get() + "\t기대: " + (concurrentRequests - 1));

        Set<String> uniqueNewTokens = new HashSet<>();
        for (AuthService.TokenPair pair : successfulPairs) {
            uniqueNewTokens.add(pair.getRefreshToken());
        }
        System.out.println("고유한 새 Refresh Token 수: " + uniqueNewTokens.size());

        if (successCount.get() > 1) {
            System.out.println("⚠️ 경고: 동시 재발급이 여러 번 성공했습니다. Token Replay 공격에 취약할 수 있습니다.");
        }
        
        assertEquals(1, successCount.get(), 
                "같은 Refresh Token으로는 1번만 재발급 성공해야 합니다. (Token Rotation 필요)");
    }

    // Helper method
    private User createAndSaveUser(String name) {
        User user = User.builder()
                .email(name + "@test.com")
                .password("password123")
                .name(name)
                .build();
        return userRepository.save(user);
    }
}
