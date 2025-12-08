package com.ticket.dojo.backdeepfamily.domain.auth.controller;

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

    /**
     * Access 토큰 재발급 API
     *
     * 동작 흐름:
     * 1. 쿠키에서 refresh 토큰 추출
     * 2. Refresh 토큰 검증 (만료 여부, DB 존재 여부)
     * 3. 새로운 access 토큰 발급
     * 4. (선택) 새로운 refresh 토큰도 발급하여 갱신 (Refresh Token Rotation)
     */
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

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

        // === 2단계: Refresh 토큰 검증 ===
        try {
            // 토큰 만료 여부 확인
            if (jwtUtil.isExpired(refreshToken)) {
                log.warn("Refresh token expired");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token expired");
            }

            // 토큰 카테고리 확인 (refresh 토큰이어야 함)
            String category = jwtUtil.getCategory(refreshToken);
            if (!"refresh".equals(category)) {
                log.warn("Invalid token category: {}", category);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token type");
            }

            // DB에 저장된 토큰인지 확인
            if (!refreshService.validateRefreshToken(refreshToken)) {
                log.warn("Refresh token not found in database or expired");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
            }

            // === 3단계: 토큰에서 사용자 정보 추출 ===
            String username = jwtUtil.getUsername(refreshToken);
            String role = jwtUtil.getRole(refreshToken);

            // 사용자 존재 여부 확인
            if (userRepository.findByEmail(username) == null) {
                log.warn("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found");
            }

            // === 4단계: 새로운 토큰 발급 ===
            // 새로운 access 토큰 발급 (10분)
            String newAccessToken = jwtUtil.createJwt("access", username, role, 600000L);

            // 새로운 refresh 토큰 발급 (24시간) - Refresh Token Rotation
            String newRefreshToken = jwtUtil.createJwt("refresh", username, role, 86400000L);

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
     * 1. 쿠키에서 refresh 토큰 추출
     * 2. DB에서 refresh 토큰 삭제
     * 3. 쿠키 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {

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

        // refresh 토큰이 없어도 로그아웃 성공 처리 (이미 로그아웃 상태)
        if (refreshToken == null) {
            log.info("No refresh token found, user already logged out");
            return ResponseEntity.ok("Logged out successfully");
        }

        // === 2단계: DB에서 refresh 토큰 삭제 ===
        try {
            refreshService.deleteRefreshToken(refreshToken);

            // === 3단계: 쿠키 삭제 ===
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
