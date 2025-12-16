package com.ticket.dojo.backdeepfamily.domain.auth.service;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.RefreshToken;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshService {

    private final RefreshRepository refreshRepository;

    /**
     * Refresh 토큰 저장
     * - 기존 토큰이 있으면 삭제 후 새로운 토큰 저장 (1유저 1토큰 정책)
     */
    @Transactional
    public void saveRefreshToken(String email, String refreshToken, LocalDateTime expiration) {
        // 기존 토큰이 있다면 삭제
        refreshRepository.findByEmail(email).ifPresent(refreshRepository::delete);

        // 새로운 refresh 토큰 저장
        RefreshToken token = RefreshToken.builder()
                .email(email)
                .refreshToken(refreshToken)
                .expiration(expiration)
                .build();

        refreshRepository.save(token);
        log.info("Refresh token saved for user: {}", email);
    }

    /**
     * Refresh 토큰 검증
     */
    public boolean validateRefreshToken(String refreshToken) {
        Optional<RefreshToken> token = refreshRepository.findByRefreshToken(refreshToken);

        if (token.isEmpty()) {
            log.warn("Refresh token not found in database");
            return false;
        }

        // 만료 시간 확인
        if (token.get().getExpiration().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token expired");
            refreshRepository.delete(token.get());
            return false;
        }

        return true;
    }

    /**
     * Refresh 토큰으로 이메일 조회
     */
    public Optional<String> getEmailByRefreshToken(String refreshToken) {
        return refreshRepository.findByRefreshToken(refreshToken)
                .map(RefreshToken::getEmail);
    }

    /**
     * Refresh 토큰 삭제 (로그아웃)
     */
    @Transactional
    public void deleteRefreshToken(String refreshToken) {
        refreshRepository.deleteByRefreshToken(refreshToken);
        log.info("Refresh token deleted");
    }

    /**
     * 이메일로 Refresh 토큰 삭제 (로그아웃)
     */
    @Transactional
    public void deleteRefreshTokenByEmail(String email) {
        refreshRepository.deleteByEmail(email);
        log.info("Refresh token deleted for user: {}", email);
    }
}
