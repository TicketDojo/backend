package com.ticket.dojo.backdeepfamily.domain.auth.scheduler;

import com.ticket.dojo.backdeepfamily.domain.auth.service.BlackListService;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 토큰 정리 스케줄러
 * - 만료된 블랙리스트 토큰 자동 정리
 * - 만료된 refresh 토큰 자동 정리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final BlackListService blackListService;
    private final RefreshRepository refreshRepository;

    /**
     * 만료된 블랙리스트 토큰 정리
     * - 매일 자정에 실행
     * - cron: "초 분 시 일 월 요일"
     * - 0 0 0 * * * : 매일 자정 (00:00:00)
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupExpiredBlacklistTokens() {
        log.info("Starting cleanup of expired blacklist tokens...");

        try {
            blackListService.removeExpiredTokens();
            log.info("Expired blacklist tokens cleanup completed successfully");
        } catch (Exception e) {
            log.error("Failed to cleanup expired blacklist tokens: {}", e.getMessage());
        }
    }

    /**
     * 만료된 Refresh 토큰 정리
     * - 매일 자정 5분에 실행
     * - cron: 0 5 0 * * * : 매일 00:05:00
     */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        log.info("Starting cleanup of expired refresh tokens...");

        try {
            LocalDateTime now = LocalDateTime.now();
            refreshRepository.findAll().stream()
                    .filter(token -> token.getExpiration().isBefore(now))
                    .forEach(refreshRepository::delete);

            log.info("Expired refresh tokens cleanup completed successfully");
        } catch (Exception e) {
            log.error("Failed to cleanup expired refresh tokens: {}", e.getMessage());
        }
    }

    /**
     * 테스트용: 매 시간마다 실행 (선택사항)
     * - 주석 해제하여 사용
     * - cron: 0 0 * * * * : 매 시간 정각
     */
    // @Scheduled(cron = "0 0 * * * *")
    // @Transactional
    // public void hourlyCleanup() {
    //     log.info("Hourly token cleanup started...");
    //     cleanupExpiredBlacklistTokens();
    //     cleanupExpiredRefreshTokens();
    // }
}
