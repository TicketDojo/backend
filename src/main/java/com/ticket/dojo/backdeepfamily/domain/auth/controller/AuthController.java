package com.ticket.dojo.backdeepfamily.domain.auth.controller;

import com.ticket.dojo.backdeepfamily.domain.auth.service.AuthService;
import com.ticket.dojo.backdeepfamily.global.util.cookie.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 API 컨트롤러
 *
 * 책임:
 * - HTTP 요청/응답 처리
 * - 쿠키 및 헤더 관리
 * - 비즈니스 로직은 AuthService에 위임
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Access 토큰 재발급 API
     *
     * 책임: 요청에서 refresh 토큰 추출 → 서비스 호출 → 응답에 토큰 설정
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refresh(HttpServletRequest request, HttpServletResponse response) {
        // 쿠키에서 refresh 토큰 추출
        String refreshToken = CookieUtil.getCookieValue(request.getCookies(), "refresh");

        // AuthService에 비즈니스 로직 위임
        AuthService.TokenPair tokenPair = authService.reissueTokens(refreshToken);

        // 응답에 토큰 설정
        response.setHeader("access", tokenPair.getAccessToken());
        response.addCookie(CookieUtil.createCookie("refresh", tokenPair.getRefreshToken()));

        return ResponseEntity.ok("Token reissued successfully");
    }

    /**
     * 로그아웃 API
     *
     * 책임: 요청에서 토큰 추출 → 서비스 호출 → 쿠키 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        // Authorization 헤더에서 access 토큰 추출
        String authorization = request.getHeader("Authorization");
        String accessToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessToken = authorization.substring(7);
        }

        // 쿠키에서 refresh 토큰 추출
        String refreshToken = CookieUtil.getCookieValue(request.getCookies(), "refresh");

        // AuthService에 비즈니스 로직 위임
        authService.logout(accessToken, refreshToken);

        // 쿠키 삭제
        response.addCookie(CookieUtil.deleteCookie("refresh"));

        return ResponseEntity.ok("Logged out successfully");
    }
}
