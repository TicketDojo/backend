package com.ticket.dojo.backdeepfamily.domain.auth.service;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.BlackListToken;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.BlackListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 블랙리스트 토큰 관리 서비스
 * - Access 토큰 블랙리스트 추가/조회/삭제
 * - 로그아웃 시 토큰 무효화
 * - 만료된 토큰 정리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlackListService {

    private final BlackListRepository blackListRepository;

    /**
     * Access 토큰을 블랙리스트에 추가
     * - 로그아웃 시 사용
     * - 토큰이 만료될 때까지 블랙리스트에 유지
     */
    @Transactional
    public void addToBlacklist(String email, String accessToken, LocalDateTime expiration) {
        BlackListToken blackListToken = BlackListToken.builder()
                .email(email)
                .accessToken(accessToken)
                .expiration(expiration)
                .build();

        blackListRepository.save(blackListToken);
        log.info("Access token added to blacklist for user: {}", email);
    }

    /**
     * Access 토큰이 블랙리스트에 있는지 확인
     * - JWTFilter에서 모든 요청마다 호출
     * - 블랙리스트에 있으면 true 반환
     */
    public boolean isBlacklisted(String accessToken) {
        return blackListRepository.existsByAccessToken(accessToken);
    }

    /**
     * 만료된 블랙리스트 토큰 정리
     * - 스케줄러에서 주기적으로 호출
     * - 현재 시간보다 이전에 만료된 토큰 삭제
     */
    @Transactional
    public void removeExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        blackListRepository.deleteByExpirationBefore(now);
        log.info("Expired blacklist tokens removed at: {}", now);
    }

    /**
     * 특정 토큰을 블랙리스트에서 삭제
     * - 필요 시 수동으로 토큰 삭제
     */
    @Transactional
    public void removeFromBlacklist(String accessToken) {
        blackListRepository.deleteByAccessToken(accessToken);
        log.info("Access token removed from blacklist");
    }
}
