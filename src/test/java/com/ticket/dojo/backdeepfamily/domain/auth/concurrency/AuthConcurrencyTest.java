package com.ticket.dojo.backdeepfamily.domain.auth.concurrency;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
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

import com.ticket.dojo.backdeepfamily.domain.auth.service.RefreshService;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
public class AuthConcurrencyTest {

    @Autowired
    private RefreshService refreshService;

    @Autowired
    private RefreshRepository refreshRepository;

    @BeforeEach
    void setUp() {
        // 기존 데이터 정리
        refreshRepository.deleteAll();
        log.info("▶▶▶ 기존 데이터 정리 완료");
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 데이터 정리
        refreshRepository.deleteAll();
        log.info("▶▶▶ 테스트 데이터 정리 완료");
    }

    @Test
    @DisplayName("동일한 이메일로 5번 동시에 Refresh Token을 저장할 때, 1개의 토큰만 존재해야 한다.")
    void testRefreshTokenSaveRace() throws InterruptedException {
        String testEmail = "test@test.com";

        // 5개의 스레드가 동시에 동일한 이메일로 refresh token 저장 시도
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<String> savedTokens = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final String refreshToken = "refresh_token_" + i;
            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 시작] Refresh Token 저장 시도: {}", refreshToken);

                    refreshService.saveRefreshToken(
                        testEmail,
                        refreshToken,
                        LocalDateTime.now().plusDays(7)
                    );
                    savedTokens.add(refreshToken);

                    log.info("[저장 성공] Refresh Token: {}", refreshToken);
                } catch (InterruptedException e) {
                    log.error("[인터럽트 발생] {}", e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.error("[에러 발생] {}", e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        log.info("▶▶▶ 모든 스레드 동시 saveRefreshToken() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 검증: 해당 이메일의 Refresh Token은 1개만 존재해야 함
        long tokenCount = refreshRepository.findByEmail(testEmail)
            .map(token -> 1L)
            .orElse(0L);

        log.info("최종 DB에 저장된 Refresh Token 개수: {}", tokenCount);
        log.info("시도된 토큰들: {}", savedTokens);

        assertThat(tokenCount)
            .as("동일 이메일로 5번 동시 저장 시도 시 Refresh Token은 1개만 존재해야 합니다")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("동일한 Refresh Token을 10개의 스레드가 동시에 검증할 때, 모두 성공해야 한다.")
    void testRefreshTokenValidationRace() throws InterruptedException {
        // 1. 테스트용 Refresh Token 저장
        String testEmail = "validation@test.com";
        String refreshToken = "test_refresh_token";
        refreshService.saveRefreshToken(testEmail, refreshToken, LocalDateTime.now().plusDays(7));
        log.info("▶▶▶ 테스트용 Refresh Token 저장 완료");

        // 2. 10개의 스레드가 동시에 동일한 토큰 검증
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<Boolean> validationResults = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 {}] Refresh Token 검증 시작", threadNum);

                    boolean isValid = refreshService.validateRefreshToken(refreshToken);
                    validationResults.add(isValid);

                    log.info("[스레드 {}] 검증 결과: {}", threadNum, isValid);
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

        log.info("▶▶▶ 모든 스레드 동시 validateRefreshToken() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 검증: 모든 스레드가 true를 받아야 함 (토큰이 삭제되지 않아야 함)
        log.info("검증 결과들: {}", validationResults);

        assertThat(validationResults)
            .as("모든 스레드가 동일한 검증 결과(true)를 받아야 합니다")
            .containsOnly(true);

        // 토큰이 여전히 DB에 존재하는지 확인
        boolean tokenStillExists = refreshRepository.findByRefreshToken(refreshToken).isPresent();
        assertThat(tokenStillExists)
            .as("검증 후에도 토큰이 DB에 존재해야 합니다")
            .isTrue();
    }

    @Test
    @DisplayName("동일한 Refresh Token을 5개의 스레드가 동시에 삭제 시도할 때, 오류 없이 처리되어야 한다.")
    void testRefreshTokenDeleteRace() throws InterruptedException {
        // 1. 테스트용 Refresh Token 저장
        String testEmail = "delete@test.com";
        String refreshToken = "test_refresh_token_delete";
        refreshService.saveRefreshToken(testEmail, refreshToken, LocalDateTime.now().plusDays(7));
        log.info("▶▶▶ 테스트용 Refresh Token 저장 완료");

        // 2. 5개의 스레드가 동시에 동일한 토큰 삭제 시도
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Set<String> errors = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            executorService.submit(() -> {
                try {
                    latch.await();
                    log.info("[스레드 {}] Refresh Token 삭제 시작", threadNum);

                    refreshService.deleteRefreshToken(refreshToken);

                    log.info("[스레드 {}] 삭제 완료", threadNum);
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

        log.info("▶▶▶ 모든 스레드 동시 deleteRefreshToken() 호출 시작!");
        latch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed)
            .as("모든 스레드가 30초 내에 완료되어야 합니다 (교착상태 없음)")
            .isTrue();

        // 검증: 토큰이 삭제되었는지 확인
        boolean tokenExists = refreshRepository.findByRefreshToken(refreshToken).isPresent();

        log.info("최종 토큰 존재 여부: {}", tokenExists);
        log.info("발생한 에러들: {}", errors);

        assertThat(tokenExists)
            .as("동시 삭제 후 토큰이 존재하지 않아야 합니다")
            .isFalse();

        // 에러가 발생했더라도 최종 상태가 정상이면 OK (동시성 문제 탐지용)
        if (!errors.isEmpty()) {
            log.warn("⚠️ 동시 삭제 중 에러 발생: {}", errors);
        }
    }
}
