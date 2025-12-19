package com.ticket.dojo.backdeepfamily.global.util.jwt;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ticket.dojo.backdeepfamily.domain.auth.service.RefreshService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 로그인 필터 (JWT 토큰 발급 담당)
 *
 * 역할:
 * - /login 경로로 POST 요청이 오면 자동으로 실행되는 필터
 * - 사용자가 입력한 아이디/비밀번호를 검증
 * - 인증 성공 시 JWT 토큰을 생성하여 응답 헤더에 추가
 * - 인증 실패 시 401 에러 반환
 *
 * 동작 흐름:
 * 1. 사용자가 /login으로 POST 요청 (username, password 포함)
 * 2. attemptAuthentication() 실행 → 인증 시도
 * 3-1. 인증 성공 → successfulAuthentication() → JWT 토큰 발급
 * 3-2. 인증 실패 → unsuccessfulAuthentication() → 401 에러
 *
 * 참고: Spring Security의 UsernamePasswordAuthenticationFilter를 상속받아
 * 기본 로그인 처리를 JWT 방식으로 커스터마이징
 */
@RequiredArgsConstructor
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

    /**
     * Spring Security의 인증 관리자
     * - 실제 사용자 인증(아이디/비밀번호 확인)을 수행
     * - DB에 저장된 사용자 정보와 비교
     */
    private final AuthenticationManager authenticationManager;

    /**
     * JWT 토큰 생성/검증 유틸리티
     * - 로그인 성공 시 JWT 토큰을 생성하는데 사용
     */
    private final JWTUtil jwtUtil;

    /**
     * Refresh 토큰 관리 서비스
     * - Refresh 토큰을 DB에 저장하고 관리
     */
    private final RefreshService refreshService;

    /**
     * 로그인 인증 시도 메서드
     *
     * 실행 시점: 사용자가 /login으로 POST 요청을 보낼 때
     *
     * @param request  HTTP 요청 (JSON 바디: email, password 포함)
     * @param response HTTP 응답
     * @return Authentication 인증 결과 객체
     *
     *         동작 과정:
     *         1. 요청의 JSON 바디에서 email과 password 추출
     *         2. UsernamePasswordAuthenticationToken 생성 (인증 전 토큰)
     *         3. AuthenticationManager에게 인증 요청
     *         4. AuthenticationManager가 CustomUserDetailsService를 호출하여
     *         DB에서 사용자 정보를 조회하고 비밀번호 검증
     *         5. 인증 성공 시 Authentication 객체 반환
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

        try {
            // JSON 요청 바디 읽기
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> loginData = objectMapper.readValue(request.getInputStream(), Map.class);

            String email = loginData.get("email");
            String password = loginData.get("password");

            // 인증 전 토큰 생성
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(email, password,
                    null);

            // AuthenticationManager에게 인증 요청
            return authenticationManager.authenticate(authToken);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse login request body", e);
        }
    }

    /**
     * 로그인 인증 성공 시 실행되는 메서드
     *
     * 실행 시점: attemptAuthentication()에서 인증이 성공한 경우
     *
     * @param request        HTTP 요청
     * @param response       HTTP 응답
     * @param chain          필터 체인 (다음 필터로 요청 전달)
     * @param authentication 인증 성공 정보 (사용자 정보, 권한 포함)
     *
     *                       동작 과정:
     *                       1. 인증된 사용자 정보(Authentication)에서 username과 role 추출
     *                       2. JWT 토큰 생성 (username, role, 유효기간 1시간)
     *                       3. 응답 헤더에 "Authorization: Bearer {토큰}" 형식으로 추가
     *                       4. 클라이언트는 이 토큰을 저장하여 이후 요청에 사용
     *
     *                       JWT 토큰 구조:
     *                       - Header.Payload.Signature 형식의 문자열
     *                       - Payload에 username, role, 발행시간, 만료시간 포함
     *                       - 서버의 비밀키로 서명되어 위조 불가능
     *
     *                       예시:
     *                       - 입력: user@example.com 로그인 성공
     *                       - 출력: Authorization: Bearer
     *                       eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6...
     *                       - 클라이언트는 이 토큰을 저장하여 다음 요청 시 헤더에 포함
     */
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authentication) throws IOException, ServletException {

        // // 인증된 사용자 정보 추출
        // // Principal은 인증의 주체(사용자)를 나타냄
        // CustomUserDetails customUserDetails = (CustomUserDetails)
        // authentication.getPrincipal();

        // // 사용자 이름 추출 (실제로는 email)
        // String username = customUserDetails.getUsername();
        // System.out.println(username); // 로그인한 사용자 확인용 출력

        // // 사용자의 권한(authorities) 목록 가져오기
        // Collection<? extends GrantedAuthority> authorities =
        // authentication.getAuthorities();

        // // 권한 목록에서 첫 번째 권한 추출
        // // 현재는 사용자당 하나의 권한만 가지므로 첫 번째 권한 사용
        // Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        // GrantedAuthority auth = iterator.next();

        // // 권한 문자열 추출 (예: "ROLE_USER", "ROLE_ADMIN")
        // String role = auth.getAuthority();

        // // JWT 토큰 생성
        // // 파라미터: username(email), role, 유효기간(1시간 = 60분 * 60초 * 1000밀리초)
        // String token = jwtUtil.createJwt(username, role, 60*60*1000L);

        // // 응답 헤더에 JWT 토큰 추가
        // // "Authorization: Bearer {토큰}" 형식
        // // Bearer는 토큰 기반 인증의 표준 접두사
        // response.addHeader("Authorization", "Bearer " + token);

        // // 클라이언트는 이 헤더에서 토큰을 추출하여 저장하고,
        // // 이후 모든 API 요청에 이 토큰을 포함시켜야 함

        String username = authentication.getName();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();

        // Access 토큰: 10분 - JWT 형식
        String access = jwtUtil.createJwt("access", username, role, 600000L);

        // Refresh 토큰: 24시간 - UUID 형식
        // JWT 대신 UUID 사용하여 토큰 탈취 시 정보 유출 방지
        String refresh = UUID.randomUUID().toString();

        // Refresh 토큰을 DB에 저장
        LocalDateTime expiration = LocalDateTime.now().plusDays(1);
        refreshService.saveRefreshToken(username, refresh, expiration);

        // 응답 헤더와 쿠키에 토큰 추가
        response.setHeader("access", access);
        response.addCookie(
                com.ticket.dojo.backdeepfamily.global.util.cookie.CookieUtil.createCookie("refresh", refresh));
        response.setStatus(HttpStatus.OK.value());
    }

    /**
     * 로그인 인증 실패 시 실행되는 메서드
     *
     * 실행 시점: attemptAuthentication()에서 인증이 실패한 경우
     *
     * @param request  HTTP 요청
     * @param response HTTP 응답
     * @param failed   인증 실패 예외 정보
     *
     *                 동작 과정:
     *                 1. HTTP 상태 코드를 401 (Unauthorized)로 설정
     *                 2. 클라이언트에게 인증 실패를 알림
     *
     *                 인증 실패 사유:
     *                 - 존재하지 않는 사용자 (email이 DB에 없음)
     *                 - 비밀번호 불일치
     *                 - 계정 잠김, 만료 등
     *
     *                 예시:
     *                 - 입력: 잘못된 비밀번호로 로그인 시도
     *                 - 결과: HTTP 401 상태 코드 반환
     *                 - 클라이언트는 "로그인 실패" 메시지 표시
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failed) throws IOException, ServletException {

        // HTTP 상태 코드 401 (Unauthorized) 설정
        // 401 = 인증되지 않음 (아이디/비밀번호가 틀림)
        response.setStatus(401);
    }

}
