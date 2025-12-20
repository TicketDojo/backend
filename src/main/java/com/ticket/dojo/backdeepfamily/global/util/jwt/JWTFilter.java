package com.ticket.dojo.backdeepfamily.global.util.jwt;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ticket.dojo.backdeepfamily.domain.auth.service.BlackListService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.CustomUserDetails;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT 토큰 검증 필터
 *
 * 역할:
 * - 모든 HTTP 요청에서 JWT 토큰을 검증하는 필터
 * - 로그인 이후의 모든 API 요청에 대해 사용자 인증 상태를 확인
 * - 유효한 토큰이 있으면 Spring Security에 인증 정보 등록
 * - 유효하지 않은 토큰이면 401 에러 반환
 *
 * 동작 흐름:
 * 1. 클라이언트가 API 요청 시 Authorization 헤더에 JWT 토큰 포함
 *    예: "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
 * 2. 이 필터가 모든 요청을 가로채서 토큰 검증
 * 3-1. 토큰이 유효하면 → Spring Security에 인증 정보 저장 → 요청 진행
 * 3-2. 토큰이 없거나 유효하지 않으면 → 401 에러 또는 요청 진행 (공개 경로)
 *
 * 참고: OncePerRequestFilter를 상속받아 요청당 한 번만 실행됨
 */
@RequiredArgsConstructor
@Slf4j
public class JWTFilter extends OncePerRequestFilter{

    /**
     * JWT 토큰 생성/검증 유틸리티
     * - 토큰에서 사용자 정보 추출
     * - 토큰 만료 여부 확인
     */
    private final JWTUtil jwtUtil;

    /**
     * 사용자 정보 조회를 위한 Repository
     * - 토큰의 username(email)으로 실제 사용자가 DB에 존재하는지 확인
     * - 삭제된 사용자의 토큰을 거부하기 위함
     */
    private final UserRepository userRepository;

    /**
     * 블랙리스트 토큰 관리 서비스
     * - 로그아웃된 토큰 확인
     * - 블랙리스트에 있는 토큰은 거부
     */
    private final BlackListService blackListService;

    /**
     * 모든 HTTP 요청에 대해 실행되는 필터 메서드
     *
     * 실행 시점: 클라이언트가 API를 호출할 때마다
     *
     * @param request HTTP 요청 (Authorization 헤더 포함)
     * @param response HTTP 응답
     * @param filterChain 다음 필터로 요청을 전달하는 체인
     *
     * 전체 동작 과정:
     * 1. 공개 경로(/login, /users/join) 확인 → JWT 검증 건너뛰기
     * 2. Authorization 헤더에서 JWT 토큰 추출
     * 3. 토큰 유효성 검증 (만료 여부, 서명 확인)
     * 4. 토큰에서 사용자 정보 추출 및 DB 조회
     * 5. Spring Security에 인증 정보 등록
     * 6. 다음 필터로 요청 전달
     *
     * 예시 요청:
     * GET /api/users/profile
     * Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InVzZXJAZXhhbXBsZS5jb20iLCJyb2xlIjoiUk9MRV9VU0VSIn0...
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 요청 경로 추출 (예: "/login", "/api/users/profile")
        String path = request.getRequestURI();

        // 공개 경로는 JWT 검증 건너뛰기
        // /login: 로그인 요청 (토큰이 없어야 정상)
        // /users/join: 회원가입 요청 (토큰이 없어야 정상)
        // /auth/reissue: 토큰 재발급 요청 (refresh 토큰만 필요)
        // /auth/logout: 로그아웃 요청
        if(path.equals("/login") || path.equals("/users/join") ||
           path.equals("/auth/reissue") || path.equals("/auth/logout")) {
            filterChain.doFilter(request, response);  // 다음 필터로 바로 전달
            return;
        }

        // Authorization 헤더 추출
        // 정상 형식: "Bearer eyJhbGciOiJIUzI1NiJ9..."
        String authorization = request.getHeader("Authorization");

        // 토큰이 없거나 "Bearer "로 시작하지 않는 경우
        if(authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("No JWT token found in request to {}", path);

            // SecurityConfig에서 해당 경로가 인증 필요하면 403 에러 발생
            // SecurityConfig에서 permitAll()이면 정상 진행
            filterChain.doFilter(request, response);
            return;
        }

        log.info("Start Authorization");  // JWT 인증 시작 로그

        // "Bearer " 접두사 제거하여 순수 토큰 문자열만 추출
        // "Bearer eyJhbGci..." → "eyJhbGci..."
        String token = authorization.split(" ")[1];

        try {
            // === 1단계: 토큰 카테고리 확인 (access 토큰만 허용) ===
            String category = jwtUtil.getCategory(token);

            // refresh 토큰으로 API 접근 시도를 차단
            if(!"access".equals(category)) {
                log.error("Invalid token category: {}", category);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // === 2단계: 토큰 만료 여부 확인 ===
            if(jwtUtil.isExpired(token)) {
                log.error("Token Timeout");

                // 401 Unauthorized: 토큰이 만료되었음을 알림
                // 클라이언트는 refresh 토큰으로 재발급해야 함
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // === 4단계: 토큰에서 사용자 정보 추출 ===
            // JWT 토큰의 Payload에서 username(실제로는 email) 추출
            String username = jwtUtil.getUsername(token);

            // === 5단계: DB에서 실제 사용자 존재 여부 확인 ===
            // 토큰이 유효하더라도 사용자가 삭제되었을 수 있음
            User user = userRepository.findByEmail(username);

            // 사용자가 DB에 존재하지 않으면 인증 실패
            if(user == null) {
                log.error("User not found: {}", username);

                // 401 Unauthorized: 사용자가 존재하지 않음
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            // === 6단계: Spring Security용 UserDetails 객체 생성 ===
            // DB에서 조회한 User 엔티티를 Spring Security가 이해할 수 있는 형태로 변환
            CustomUserDetails customUserDetails = new CustomUserDetails(user);

            // === 7단계: 인증 토큰 생성 ===
            // Spring Security의 인증 객체 생성
            // 파라미터: 사용자정보, 비밀번호(null - 이미 인증됨), 권한목록
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                customUserDetails,  // 인증된 사용자 정보
                null,  // 비밀번호 (JWT 인증에서는 불필요)
                customUserDetails.getAuthorities()  // 사용자 권한 (ROLE_USER, ROLE_ADMIN 등)
            );

            // === 8단계: Spring Security에 인증 정보 등록 ===
            // SecurityContextHolder: 현재 요청의 인증 정보를 저장하는 저장소
            // 이후 컨트롤러에서 @AuthenticationPrincipal로 사용자 정보 접근 가능
            SecurityContextHolder.getContext().setAuthentication(authToken);

            // === 9단계: 다음 필터로 요청 전달 ===
            // 인증이 완료되었으므로 다음 필터/컨트롤러로 요청 전달
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // JWT 파싱 실패, 서명 검증 실패 등의 예외 처리
            // 예: 잘못된 형식의 토큰, 조작된 토큰, 만료된 토큰 등
            log.error("JWT token validation failed: {}", e.getMessage());

            // 401 Unauthorized: 토큰 검증 실패
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            // 주의: return을 하지 않으면 다음 필터로 진행되어 문제가 발생할 수 있음
            // 현재는 401 응답만 설정하고 필터 체인이 계속 진행됨
        }
    }

    // 아래 경로에서는 jwtFilter가 동작하지 않음
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (path.startsWith("/actuator")) return true;

        return path.equals("/login")
                || path.equals("/users/join")
                || path.equals("/auth/reissue")
                || path.equals("/auth/logout");
    }


}
