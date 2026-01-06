package com.ticket.dojo.backdeepfamily.domain.auth.racecondition;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.RefreshToken;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;
import com.ticket.dojo.backdeepfamily.domain.auth.service.RefreshService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Auth Race Condition 테스트")
class AuthRaceConditionTest {

    @Autowired
    private RefreshService refreshService;

    @Autowired
    private RefreshRepository refreshRepository;

    @AfterEach
    void cleanup() {
        refreshRepository.deleteAll();
    }

    @Test
    @DisplayName("Race Condition: 동일 이메일 동시 RefreshToken 저장 시 삭제 안되고 여러개 생성되는 문제")
    void concurrentTokenSave() {
        // given
        String email = "race_test@test.com";
        LocalDateTime expiration = LocalDateTime.now().plusDays(1);

        // when
        // 10번 동시에 같은 이메일로 토큰 저장 시도
        int concurrentAttempts = 10;
        ExecutorService executorService = Executors.newCachedThreadPool();
        Phaser phaser = new Phaser(concurrentAttempts + 1);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < concurrentAttempts; i++) {
            executorService.submit(() -> {
                try {
                    phaser.arriveAndAwaitAdvance();

                    String refreshToken = UUID.randomUUID().toString();
                    refreshService.saveRefreshToken(email, refreshToken, expiration);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("예외 발생: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        phaser.arriveAndAwaitAdvance();
        phaser.arriveAndAwaitAdvance();

        // then
        List<RefreshToken> tokens = refreshRepository.findAll();
        int tokenCount = tokens.size();

        // 같은 이메일의 토큰 개수
        long sameEmailTokenCount = tokens.stream()
                .filter(token -> token.getEmail().equals(email))
                .count();

        System.err.println("=== Results ===");
        System.err.println("동시 시도 횟수: " + concurrentAttempts);
        System.err.println("성공 횟수: " + successCount.get());
        System.err.println("실제 저장된 토큰 수: " + tokenCount);
        System.err.println("같은 이메일 토큰 수: " + sameEmailTokenCount);

        // Race Condition 발생 시: 여러 토큰 생성됨
        // 정상: 1개만 생성되어야 함
        assertThat(tokenCount).isEqualTo(1);
    }
}
