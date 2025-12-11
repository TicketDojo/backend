package com.ticket.dojo.backdeepfamily.domain.auth.service;

import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.AuthException;
import com.ticket.dojo.backdeepfamily.global.util.jwt.JWTUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 인증 관련 비즈니스 로직 서비스
 *
 * 책임:
 * - 토큰 재발급 로직
 * - 로그아웃 로직
 * - 예외는 RuntimeException으로 던져서 GlobalExceptionHandler에서 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JWTUtil jwtUtil;
    private final RefreshService refreshService;
    private final UserRepository userRepository;
    private final BlackListService blackListService;

    /**
     * 토큰 재발급
     *
     * @param refreshToken UUID 형식의 refresh 토큰
     * @return 새로운 access 토큰과 refresh 토큰 쌍
     * @throws AuthException.InvalidRefreshTokenException refresh 토큰이 유효하지 않을 때
     */
    @Transactional
    public TokenPair reissueTokens(String refreshToken) {
        // 1. Refresh 토큰 null 체크
        if (refreshToken == null) {
            throw new AuthException.InvalidRefreshTokenException("Refresh token is missing");
        }

        // 2. DB에서 refresh 토큰 검증
        if (!refreshService.validateRefreshToken(refreshToken)) {
            log.warn("Refresh token not found in database or expired");
            throw new AuthException.InvalidRefreshTokenException("Invalid or expired refresh token");
        }

        // 3. DB에서 사용자 정보 조회
        String username = refreshService.getEmailByRefreshToken(refreshToken)
                .orElseThrow(() -> new AuthException.InvalidRefreshTokenException("User not found for refresh token"));

        User user = userRepository.findByEmail(username);
        if (user == null) {
            log.warn("User not found: {}", username);
            throw new AuthException.InvalidRefreshTokenException("User not found");
        }

        // 4. Role 변환 (ROLE_ 접두사 추가)
        String role = "ROLE_" + user.getRole().name();

        // 5. 새로운 토큰 생성
        String newAccessToken = jwtUtil.createJwt("access", username, role, 600000L); // 10분
        String newRefreshToken = UUID.randomUUID().toString();

        // 6. Refresh Token Rotation: 기존 토큰 삭제하고 새 토큰 저장
        LocalDateTime expiration = LocalDateTime.now().plusDays(1);
        refreshService.saveRefreshToken(username, newRefreshToken, expiration);

        log.info("Token reissued successfully for user: {}", username);
        return new TokenPair(newAccessToken, newRefreshToken);
    }

    /**
     * 로그아웃 처리
     *
     * @param accessToken JWT access 토큰 (null 가능)
     * @param refreshToken UUID refresh 토큰 (null 가능)
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // 1. Access 토큰을 블랙리스트에 추가
        if (accessToken != null) {
            try {
                String email = jwtUtil.getUsername(accessToken);
                LocalDateTime expiration = LocalDateTime.now().plusMinutes(10); // Access 토큰 만료시간
                blackListService.addToBlacklist(email, accessToken, expiration);
                log.info("Access token added to blacklist for user: {}", email);
            } catch (Exception e) {
                log.warn("Failed to add access token to blacklist: {}", e.getMessage());
            }
        }

        // 2. Refresh 토큰을 DB에서 삭제
        if (refreshToken != null) {
            refreshService.deleteRefreshToken(refreshToken);
            log.info("Refresh token deleted from database");
        }

        log.info("User logged out successfully");
    }

    /**
     * 토큰 쌍 DTO
     */
    @Getter
    @RequiredArgsConstructor
    public static class TokenPair {
        private final String accessToken;
        private final String refreshToken;
    }
}
