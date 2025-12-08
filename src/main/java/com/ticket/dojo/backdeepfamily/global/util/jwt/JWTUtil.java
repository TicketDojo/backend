package com.ticket.dojo.backdeepfamily.global.util.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;

/**
 * JWT(JSON Web Token) 유틸리티 클래스
 *
 * JWT란?
 * - 사용자 인증 정보를 안전하게 전달하기 위한 토큰 기반 인증 방식
 * - 서버가 사용자 세션을 저장하지 않아도 되므로 서버 부담이 적음 (Stateless)
 * - 토큰에 사용자 정보(username, role 등)를 담아서 전달
 *
 * 주요 기능:
 * 1. JWT 토큰 생성 (createJwt)
 * 2. JWT 토큰에서 정보 추출 (getUsername, getRole)
 * 3. JWT 토큰 유효성 검증 (isExpired)
 */
@Component
public class JWTUtil {

    /**
     * JWT 토큰을 암호화/복호화하는데 사용되는 비밀 키
     * - 이 키로 토큰에 서명하여 위조를 방지
     * - application.properties의 spring.jwt.secret 값을 사용
     */
    private SecretKey secretKey;

    /**
     * 생성자: JWT 비밀 키를 초기화
     *
     * @param secret application.properties에서 주입받은 비밀키 문자열
     *
     * 동작 과정:
     * 1. 설정 파일의 비밀키 문자열을 바이트 배열로 변환
     * 2. HS256 알고리즘용 SecretKey 객체로 변환
     * 3. HS256: HMAC-SHA256 암호화 알고리즘 (대칭키 방식)
     */
    public JWTUtil(@Value("${spring.jwt.secret}")String secret) {
        secretKey = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            Jwts.SIG.HS256.key().build().getAlgorithm()
        );
    }

    /**
     * JWT 토큰에서 사용자 이름(username) 추출
     *
     * @param token 검증할 JWT 토큰 문자열
     * @return 토큰에 저장된 username (실제로는 email)
     *
     * 동작 과정:
     * 1. JWT 파서를 생성하고 비밀키로 서명 검증
     * 2. 토큰을 파싱하여 Claims(payload) 추출
     * 3. Claims에서 "username" 값을 문자열로 반환
     *
     * 참고: username이라는 이름이지만 실제로는 email 값이 저장되어 있음
     */
    public String getUsername(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)  // 비밀키로 토큰 서명 검증
            .build()
            .parseSignedClaims(token)  // 토큰 파싱
            .getPayload()  // Payload(Claims) 추출
            .get("username", String.class);  // username 값 반환
    }

    /**
     * JWT 토큰에서 사용자 권한(role) 추출
     *
     * @param token 검증할 JWT 토큰 문자열
     * @return 토큰에 저장된 role (예: "ROLE_USER", "ROLE_ADMIN")
     *
     * 동작 과정:
     * 1. JWT 파서를 생성하고 비밀키로 서명 검증
     * 2. 토큰을 파싱하여 Claims(payload) 추출
     * 3. Claims에서 "role" 값을 문자열로 반환
     */
    public String getRole(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get("role", String.class);
    }

    /**
     * JWT 토큰의 만료 여부 확인
     *
     * @param token 검증할 JWT 토큰 문자열
     * @return true: 토큰이 만료됨, false: 토큰이 유효함
     *
     * 동작 과정:
     * 1. JWT 파서를 생성하고 비밀키로 서명 검증
     * 2. 토큰을 파싱하여 만료 시간(expiration) 추출
     * 3. 만료 시간이 현재 시간보다 이전인지 확인
     *
     * 예시:
     * - 토큰 만료시간: 2025-12-08 14:00
     * - 현재 시간: 2025-12-08 15:00
     * - 결과: true (만료됨)
     */
    public Boolean isExpired(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getExpiration()  // 만료 시간 추출
            .before(new Date());  // 현재 시간보다 이전인지 확인
    }

    public String getCategory(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("category", String.class);
    }

    // /**
    //  * 새로운 JWT 토큰 생성
    //  *
    //  * @param username 사용자 이름 (실제로는 email)
    //  * @param role 사용자 권한 (예: "ROLE_USER")
    //  * @param expiredMs 토큰 유효 시간 (밀리초 단위, 예: 3600000 = 1시간)
    //  * @return 생성된 JWT 토큰 문자열
    //  *
    //  * 동작 과정:
    //  * 1. JWT 빌더 생성
    //  * 2. Claims(토큰에 담을 정보)에 username과 role 추가
    //  * 3. 발행 시간(issuedAt)을 현재 시간으로 설정
    //  * 4. 만료 시간(expiration)을 현재시간 + expiredMs로 설정
    //  * 5. 비밀키로 서명(signWith)하여 위조 방지
    //  * 6. 최종 토큰 문자열 생성(compact)
    //  *
    //  * 생성되는 토큰 구조:
    //  * - Header: 토큰 타입과 암호화 알고리즘 정보
    //  * - Payload: username, role, 발행시간, 만료시간 등
    //  * - Signature: 비밀키로 생성한 서명
    //  *
    //  * 예시:
    //  * createJwt("user@example.com", "ROLE_USER", 3600000)
    //  * → "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InVzZXJAZXhhbXBsZS5jb20iLCJyb2xlIjoiUk9MRV9VU0VSIiwiaWF0IjoxNzAzNzU4ODAwLCJleHAiOjE3MDM3NjI0MDB9.xxx..."
    //  */
    // public String createJwt(String username, String role, Long expiredMs) {
    //     return Jwts.builder()
    //         .claim("username", username)  // 토큰에 username(실제로는 email) 저장
    //         .claim("role", role)  // 토큰에 권한 정보 저장
    //         .issuedAt(new Date(System.currentTimeMillis()))  // 토큰 발행 시간
    //         .expiration(new Date(System.currentTimeMillis() + expiredMs))  // 토큰 만료 시간
    //         .signWith(secretKey)  // 비밀키로 서명하여 위조 방지
    //         .compact();  // 최종 토큰 문자열 생성
    // }

    public String createJwt(String category, String username, String role, Long expiredMs) {
        return Jwts.builder()
            .claim("category", category)
            .claim("username", username)
            .claim("role", role)
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + expiredMs))
            .signWith(secretKey)
            .compact();
    }
}
