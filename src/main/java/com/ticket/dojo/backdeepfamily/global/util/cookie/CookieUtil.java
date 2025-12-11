package com.ticket.dojo.backdeepfamily.global.util.cookie;

import jakarta.servlet.http.Cookie;

/**
 * 쿠키 생성 및 관리 유틸리티
 *
 * 책임:
 * - 쿠키 생성 로직 중앙화
 * - 보안 설정 일관성 유지
 */
public class CookieUtil {

    private CookieUtil() {
        // Utility class - 인스턴스 생성 방지
    }

    /**
     * HttpOnly 쿠키 생성
     *
     * @param key 쿠키 이름
     * @param value 쿠키 값
     * @return HttpOnly 설정된 쿠키
     */
    public static Cookie createCookie(String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(24 * 60 * 60); // 24시간
        cookie.setHttpOnly(true); // XSS 공격 방어
        // cookie.setSecure(true); // HTTPS 환경에서 활성화
        cookie.setPath("/");
        return cookie;
    }

    /**
     * 쿠키 삭제용 쿠키 생성 (MaxAge=0)
     *
     * @param key 쿠키 이름
     * @return MaxAge가 0인 쿠키 (즉시 삭제)
     */
    public static Cookie deleteCookie(String key) {
        Cookie cookie = new Cookie(key, null);
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        return cookie;
    }

    /**
     * 쿠키 배열에서 특정 쿠키 값 추출
     *
     * @param cookies 쿠키 배열
     * @param key 찾을 쿠키 이름
     * @return 쿠키 값 (없으면 null)
     */
    public static String getCookieValue(Cookie[] cookies, String key) {
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (key.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
