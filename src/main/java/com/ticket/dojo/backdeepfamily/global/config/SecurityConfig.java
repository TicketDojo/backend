package com.ticket.dojo.backdeepfamily.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.util.jwt.JWTFilter;
import com.ticket.dojo.backdeepfamily.global.util.jwt.JWTUtil;
import com.ticket.dojo.backdeepfamily.global.util.jwt.LoginFilter;

/**
 * Spring Security 설정 클래스 (JWT 인증 방식)
 *
 * 역할:
 * - Spring Security의 보안 설정 담당
 * - JWT 기반 인증 방식 구성
 * - CORS(Cross-Origin Resource Sharing) 설정
 * - 접근 권한 관리 (어떤 경로가 인증이 필요한지)
 * - 세션 비활성화 (Stateless 방식)
 *
 * JWT 인증 흐름:
 * 1. 사용자가 /login으로 로그인 요청
 * 2. LoginFilter가 인증 후 JWT 토큰 발급
 * 3. 클라이언트는 토큰을 저장
 * 4. 이후 모든 요청에 Authorization 헤더에 토큰 포함
 * 5. JWTFilter가 모든 요청의 토큰을 검증
 * 6. 유효한 토큰이면 요청 처리, 아니면 401 에러
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * Spring Security의 인증 설정 정보
     * - AuthenticationManager를 생성하는데 사용
     */
    private final AuthenticationConfiguration authenticationConfiguration;

    /**
     * JWT 토큰 생성/검증 유틸리티
     * - LoginFilter와 JWTFilter에서 사용
     */
    private final JWTUtil jwtUtil;

    /**
     * 사용자 정보 조회 Repository
     * - JWTFilter에서 토큰의 사용자 정보 검증에 사용
     */
    private final UserRepository userRepository;

    /**
     * AuthenticationManager 빈 등록
     *
     * 역할:
     * - 사용자 인증을 처리하는 핵심 관리자
     * - LoginFilter에서 로그인 시 사용자 인증에 사용
     * - CustomUserDetailsService를 호출하여 DB에서 사용자 정보 조회
     *
     * @param configuration Spring Security의 인증 설정
     * @return AuthenticationManager 인증 관리자 객체
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * BCrypt 비밀번호 암호화 빈 등록
     *
     * 역할:
     * - 비밀번호를 안전하게 암호화/검증
     * - 회원가입 시: 평문 비밀번호를 암호화하여 DB에 저장
     * - 로그인 시: 입력한 비밀번호와 DB의 암호화된 비밀번호 비교
     *
     * BCrypt 특징:
     * - 단방향 암호화 (복호화 불가능)
     * - Salt 자동 생성 (같은 비밀번호도 매번 다른 해시값 생성)
     * - 보안성이 높아 현재 표준으로 사용됨
     *
     * 예시:
     * - 평문: "password123"
     * - 암호화: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS(Cross-Origin Resource Sharing) 설정
     *
     * 역할:
     * - 다른 도메인에서 API 호출을 허용하기 위한 설정
     * - 웹 브라우저의 보안 정책(Same-Origin Policy)을 우회
     *
     * CORS란?
     * - 브라우저는 기본적으로 다른 도메인의 리소스 접근을 차단
     * - 예: http://localhost:3000 (React)에서 http://localhost:8080 (Spring) API 호출
     * - CORS 설정으로 특정 도메인의 접근을 허용
     *
     * 현재 설정:
     * - 모든 도메인(*) 허용
     * - 모든 HTTP 메서드(GET, POST, PUT, DELETE 등) 허용
     * - 모든 헤더 허용
     * - 쿠키/인증 정보 포함 가능 (credentials: true)
     * - Authorization 헤더를 클라이언트에 노출 (JWT 토큰 전달용)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 모든 Origin(도메인)에서의 요청 허용
        // 예: http://localhost:3000, https://example.com 등
        configuration.addAllowedOriginPattern("*");

        // 모든 HTTP 헤더 허용
        // 예: Content-Type, Authorization 등
        configuration.addAllowedHeader("*");

        // 모든 HTTP 메서드 허용
        // 예: GET, POST, PUT, DELETE, PATCH 등
        configuration.addAllowedMethod("*");

        // 쿠키나 인증 정보 포함 허용
        // true: 클라이언트가 credentials를 포함하여 요청 가능
        configuration.setAllowCredentials(true);

        // 클라이언트에게 노출할 응답 헤더 지정
        // Authorization 헤더를 노출해야 클라이언트가 JWT 토큰을 읽을 수 있음
        // 로그인 성공 시 응답 헤더의 "Authorization: Bearer {토큰}"을 읽기 위함
        configuration.addExposedHeader("Authorization");

        // 모든 경로("/**")에 대해 위 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Spring Security 필터 체인 설정 (핵심 보안 설정)
     *
     * 역할:
     * - HTTP 보안 설정 (CSRF, CORS, 세션 등)
     * - URL별 접근 권한 설정
     * - JWT 인증 필터 등록
     *
     * 필터 체인이란?
     * - HTTP 요청이 컨트롤러에 도달하기 전에 거치는 필터들의 연결고리
     * - 각 필터가 순서대로 요청을 검사하고 처리
     *
     * 필터 실행 순서:
     * 1. CorsFilter (CORS 검증)
     * 2. JWTFilter (JWT 토큰 검증) ← 커스텀 필터
     * 3. LoginFilter (로그인 처리) ← 커스텀 필터
     * 4. AuthorizationFilter (접근 권한 검증)
     * 5. 컨트롤러 실행
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // === CORS 설정 적용 ===
        // 위에서 정의한 corsConfigurationSource() 설정 사용
        http.cors((auth) -> auth.configurationSource(corsConfigurationSource()));

        // === CSRF(Cross-Site Request Forgery) 보호 비활성화 ===
        // CSRF란?
        // - 사용자가 의도하지 않은 요청을 강제로 실행하는 공격
        // - 일반적으로 세션 기반 인증에서 필요
        //
        // JWT 인증에서는 왜 비활성화?
        // - JWT는 Stateless 방식 (서버에 세션 저장 안 함)
        // - 토큰을 Authorization 헤더로 전송 (쿠키 사용 안 함)
        // - CSRF 공격은 주로 쿠키 기반 세션을 노림
        // - 따라서 JWT 방식에서는 CSRF 보호가 불필요
        http.csrf((auth) -> auth.disable());

        // === Form 로그인 비활성화 ===
        // 기본 Form 로그인 화면 사용 안 함
        // JWT 방식에서는 JSON으로 로그인 처리
        http.formLogin((auth) -> auth.disable());

        // === HTTP Basic 인증 비활성화 ===
        // HTTP Basic: Authorization 헤더에 "Basic {base64(id:pw)}" 형식으로 인증
        // JWT 방식 사용하므로 비활성화
        http.httpBasic((auth) -> auth.disable());

        // === URL별 접근 권한 설정 ===
        http.authorizeHttpRequests((auth) -> auth
                // 공개 경로: 인증 없이 접근 가능
                // /users/join: 회원가입
                // /login: 로그인
                .requestMatchers("/users/join", "/login").permitAll()

                // 그 외 모든 경로: 인증 필요
                // 예: /api/users/profile, /api/orders 등
                // JWT 토큰이 있어야만 접근 가능
                .anyRequest().authenticated()
        );

        // === JWT 검증 필터 추가 ===
        // LoginFilter 실행 전에 JWTFilter가 먼저 실행되도록 설정
        // JWTFilter: 모든 요청의 JWT 토큰을 검증
        // - 토큰이 유효하면 Spring Security에 인증 정보 등록
        // - 토큰이 없거나 유효하지 않으면 401 에러 또는 다음 필터로 전달
        http.addFilterBefore(new JWTFilter(jwtUtil, userRepository), LoginFilter.class);

        // === 로그인 필터 추가 ===
        // UsernamePasswordAuthenticationFilter 위치에 커스텀 LoginFilter 배치
        // LoginFilter: /login 경로의 POST 요청을 처리
        // - 사용자 인증 후 JWT 토큰 발급
        // - 응답 헤더에 "Authorization: Bearer {토큰}" 추가
        http.addFilterAt(
            new LoginFilter(authenticationManager(authenticationConfiguration), jwtUtil),
            UsernamePasswordAuthenticationFilter.class
        );

        // === 세션 관리 정책: STATELESS ===
        // Stateless란?
        // - 서버가 사용자의 상태(세션)를 저장하지 않음
        // - 모든 요청은 독립적이며 JWT 토큰으로만 인증
        //
        // JWT 인증의 핵심 원칙:
        // - 서버는 세션을 생성하지 않음
        // - 서버는 JWT 토큰만 검증
        // - 확장성과 성능에 유리 (여러 서버 간 세션 공유 불필요)
        //
        // Stateless vs Stateful:
        // - Stateful(세션 방식): 서버가 로그인 정보를 메모리/DB에 저장
        // - Stateless(JWT 방식): 서버는 저장 안 함, 토큰에 모든 정보 포함
        http.sessionManagement((session) ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // 설정된 HttpSecurity 객체를 빌드하여 SecurityFilterChain 반환
        return http.build();
    }
}
