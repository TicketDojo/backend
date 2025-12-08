package com.ticket.dojo.backdeepfamily.domain.auth.controller;

import com.ticket.dojo.backdeepfamily.domain.auth.service.BlackListService;
import com.ticket.dojo.backdeepfamily.domain.auth.service.RefreshService;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.util.jwt.JWTUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ticket.dojo.backdeepfamily.domain.user.entity.User;

/**
 * 인증 관련 API 컨트롤러
 * - 토큰 재발급
 * - 로그아웃
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JWTUtil jwtUtil;
    private final RefreshService refreshService;
    private final UserRepository userRepository;
    private final BlackListService blackListService;

    /**
     * Access 토큰 재발급 API
     *
     * 동작 흐름:
     * 1. 쿠키에서 refresh 토큰 추출
     * 2. Refresh 토큰 검증 (만료 여부, DB 존재 여부)
     * 3. 새로운 access 토큰 발급
     * 4. (선택) 새로운 refresh 토큰도 발급하여 갱신 (Refresh Token Rotation)
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {

        // === 1단계: 쿠키에서 refresh 토큰 추출 ===
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        // refresh 토큰이 없으면 에러
        if (refreshToken == null) {
            log.warn("Refresh token not found in cookies");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token is missing");
        }

        // === 2단계: Refresh 토큰 검증 (UUID 형식) ===
        try {
            // DB에서 refresh 토큰 검증 및 사용자 정보 조회
            if (!refreshService.validateRefreshToken(refreshToken)) {
                log.warn("Refresh token not found in database or expired");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
            }

            // === 3단계: DB에서 사용자 정보 조회 ===
            // Refresh 토큰은 이제 UUID이므로 DB에서 사용자 정보 조회
            String username = refreshService.getEmailByRefreshToken(refreshToken)
                    .orElseThrow(() -> new RuntimeException("User not found for refresh token"));

            // 사용자 존재 여부 및 권한 확인
            User user = userRepository.findByEmail(username);
            if (user == null) {
                log.warn("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
            }

            // Role을 문자열로 변환 (ROLE_ 접두사 추가)
            String role = "ROLE_" + user.getRole().name();

            // === 4단계: 새로운 토큰 발급 ===
            // 새로운 access 토큰 발급 (10분) - JWT 형식
            String newAccessToken = jwtUtil.createJwt("access", username, role, 600000L);

            // 새로운 refresh 토큰 발급 (24시간) - UUID 형식 (Refresh Token Rotation)
            String newRefreshToken = UUID.randomUUID().toString();

            // === 5단계: 기존 refresh 토큰 삭제하고 새 토큰 저장 ===
            LocalDateTime expiration = LocalDateTime.now().plusDays(1);
            refreshService.saveRefreshToken(username, newRefreshToken, expiration);

            // === 6단계: 응답에 토큰 추가 ===
            response.setHeader("access", newAccessToken);
            response.addCookie(createCookie("refresh", newRefreshToken));

            log.info("Token reissued successfully for user: {}", username);
            return ResponseEntity.ok("Token reissued successfully");

        } catch (Exception e) {
            log.error("Token reissue failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token reissue failed");
        }
    }

    /**
     * 로그아웃 API
     *
     * 동작 흐름:
     * 1. Authorization 헤더에서 access 토큰 추출
     * 2. Access 토큰을 블랙리스트에 추가
     * 3. 쿠키에서 refresh 토큰 추출 및 DB에서 삭제
     * 4. 쿠키 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {

        // === 1단계: Authorization 헤더에서 access 토큰 추출 ===
        String authorization = request.getHeader("Authorization");
        String accessToken = null;

        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7); // "Bearer " 제거
        }

        // === 2단계: 쿠키에서 refresh 토큰 추출 ===
        String refreshToken = null;
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refresh".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        try {
            // === 3단계: Access 토큰을 블랙리스트에 추가 ===
            if (accessToken != null) {
                // JWT에서 사용자 정보와 만료 시간 추출
                String email = jwtUtil.getUsername(accessToken);

                // 토큰 만료 시간까지 블랙리스트에 유지
                // JWT의 만료 시간을 그대로 사용
                LocalDateTime expiration = LocalDateTime.now().plusMinutes(10); // Access 토큰은 10분

                blackListService.addToBlacklist(email, accessToken, expiration);
                log.info("Access token added to blacklist for user: {}", email);
            }

            // === 4단계: DB에서 refresh 토큰 삭제 ===
            if (refreshToken != null) {
                refreshService.deleteRefreshToken(refreshToken);
                log.info("Refresh token deleted from database");
            }

            // === 5단계: 쿠키 삭제 ===
            Cookie cookie = new Cookie("refresh", null);
            cookie.setMaxAge(0);
            cookie.setPath("/");
            response.addCookie(cookie);

            log.info("User logged out successfully");
            return ResponseEntity.ok("Logged out successfully");

        } catch (Exception e) {
            log.error("Logout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Logout failed");
        }
    }

    /**
     * 쿠키 생성 헬퍼 메서드
     */
    private Cookie createCookie(String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(24 * 60 * 60); // 24시간
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        return cookie;
    }
}
