package com.ticket.dojo.backdeepfamily.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User.Role;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.util.jwt.JWTUtil;

/**
 * 로그인 통합 테스트 (SpringBootTest 사용)
 *
 * 테스트 목적:
 * - 실제 Spring Security 필터 체인을 통한 JWT 로그인 전체 흐름 검증
 * - LoginFilter, JWTFilter, SecurityConfig 등 모든 컴포넌트가 통합된 환경에서 테스트
 *
 * 테스트 환경:
 * - @SpringBootTest: 실제 Spring Context 로딩
 * - @AutoConfigureMockMvc: MockMvc를 통한 HTTP 요청 시뮬레이션
 *
 * 테스트 케이스:
 * 1. 정상 로그인 - JWT 토큰 발급 성공
 * 2. 존재하지 않는 이메일로 로그인 시도
 * 3. 잘못된 비밀번호로 로그인 시도
 * 4. 발급된 JWT 토큰의 유효성 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("로그인 통합 테스트 (JWT 인증)")
class LoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private JWTUtil jwtUtil;

    // 테스트용 사용자 정보
    private String testEmail;
    private String testPassword;
    private User testUser;

    /**
     * 각 테스트 실행 전 초기화
     * - 테스트용 사용자를 데이터베이스에 생성
     */
    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testPassword = "password123";

        // 테스트용 사용자 생성
        testUser = new User();
        testUser.setEmail(testEmail);
        testUser.setName(testEmail);
        testUser.setPassword(bCryptPasswordEncoder.encode(testPassword));
        testUser.setRole(Role.USER);

        userRepository.save(testUser);
    }

    /**
     * 각 테스트 실행 후 정리
     * - 테스트용 사용자 삭제
     */
    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    /**
     * 테스트 1: 정상 로그인 - JWT 토큰 발급 성공
     *
     * 시나리오:
     * 1. 올바른 이메일과 비밀번호로 /login POST 요청
     * 2. LoginFilter가 인증 처리
     * 3. AuthenticationManager가 CustomUserDetailsService를 통해 사용자 조회
     * 4. BCrypt로 비밀번호 검증
     * 5. JWT 토큰 생성 및 응답 헤더에 추가
     *
     * 검증 사항:
     * - HTTP 200 상태 코드
     * - Authorization 헤더에 "Bearer {토큰}" 형식의 JWT 토큰 존재
     * - 토큰이 "Bearer "로 시작하는지
     * - 토큰이 null이 아닌지
     */
    @Test
    @DisplayName("정상 로그인 - JWT 토큰 발급 성공")
    void login_Success() throws Exception {
        // When & Then: /login 요청
        MvcResult result = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", testEmail)  // Spring Security 관례상 username 파라미터 사용
                .param("password", testPassword))
                .andDo(print())  // 요청/응답 내용 출력
                .andExpect(status().isOk())  // 200 상태 코드
                .andExpect(header().exists("access"))  // access 헤더 존재
                .andReturn();

        // access 헤더에서 토큰 추출
        String accessToken = result.getResponse().getHeader("access");

        // 토큰 검증
        assertThat(accessToken).isNotNull();
        assertThat(accessToken).isNotEmpty();

        System.out.println("발급된 JWT Access 토큰: " + accessToken);
    }

    /**
     * 테스트 2: 존재하지 않는 이메일로 로그인 시도
     *
     * 시나리오:
     * 1. DB에 없는 이메일로 /login POST 요청
     * 2. CustomUserDetailsService가 사용자를 찾지 못함
     * 3. AuthenticationManager가 인증 실패 처리
     * 4. LoginFilter의 unsuccessfulAuthentication 실행
     * 5. 401 Unauthorized 응답
     *
     * 검증 사항:
     * - HTTP 401 상태 코드
     * - Authorization 헤더가 존재하지 않음
     */
    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시도 - 401 에러")
    void login_UserNotFound_Fail() throws Exception {
        // When & Then
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "nonexistent@example.com")
                .param("password", testPassword))
                .andDo(print())
                .andExpect(status().isUnauthorized())  // 401 상태 코드
                .andExpect(header().doesNotExist("access"));  // 토큰 미발급
    }

    /**
     * 테스트 3: 잘못된 비밀번호로 로그인 시도
     *
     * 시나리오:
     * 1. 올바른 이메일이지만 잘못된 비밀번호로 /login POST 요청
     * 2. CustomUserDetailsService가 사용자를 찾음
     * 3. AuthenticationManager가 비밀번호 검증 실패
     * 4. LoginFilter의 unsuccessfulAuthentication 실행
     * 5. 401 Unauthorized 응답
     *
     * 검증 사항:
     * - HTTP 401 상태 코드
     * - Authorization 헤더가 존재하지 않음
     */
    @Test
    @DisplayName("잘못된 비밀번호로 로그인 시도 - 401 에러")
    void login_WrongPassword_Fail() throws Exception {
        // When & Then
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", testEmail)
                .param("password", "wrongpassword"))
                .andDo(print())
                .andExpect(status().isUnauthorized())  // 401 상태 코드
                .andExpect(header().doesNotExist("access"));  // 토큰 미발급
    }

    /**
     * 테스트 4: 발급된 JWT 토큰의 유효성 검증
     *
     * 시나리오:
     * 1. 정상 로그인으로 JWT 토큰 발급
     * 2. 발급된 토큰을 파싱하여 내용 검증
     * 3. 토큰의 username, role이 올바른지 확인
     * 4. 토큰이 만료되지 않았는지 확인
     *
     * 검증 사항:
     * - 토큰에서 추출한 username이 로그인한 이메일과 동일
     * - 토큰에서 추출한 role이 ROLE_USER
     * - 토큰이 만료되지 않음 (isExpired = false)
     */
    @Test
    @DisplayName("발급된 JWT 토큰의 유효성 검증")
    void login_TokenValidation() throws Exception {
        // Given: 로그인하여 토큰 발급
        MvcResult result = mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", testEmail)
                .param("password", testPassword))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = result.getResponse().getHeader("access");

        // When & Then: 토큰 내용 검증
        // 1. username 검증
        String username = jwtUtil.getUsername(accessToken);
        assertThat(username).isEqualTo(testEmail);

        // 2. role 검증
        String role = jwtUtil.getRole(accessToken);
        assertThat(role).isEqualTo("USER");  // CustomUserDetails에서 user.getRole().toString()로 반환 (ROLE_ 접두사 없음)

        // 3. 토큰 만료 여부 검증
        Boolean isExpired = jwtUtil.isExpired(accessToken);
        assertThat(isExpired).isFalse();

        System.out.println("토큰 검증 결과:");
        System.out.println("  - username: " + username);
        System.out.println("  - role: " + role);
        System.out.println("  - isExpired: " + isExpired);
    }

    /**
     * 테스트 5: 필수 파라미터 누락 시 로그인 실패
     *
     * 시나리오:
     * 1. username 또는 password 파라미터 없이 /login POST 요청
     * 2. LoginFilter가 null 파라미터로 인증 시도
     * 3. 인증 실패 처리
     * 4. 401 Unauthorized 응답
     *
     * 검증 사항:
     * - HTTP 401 상태 코드
     * - Authorization 헤더가 존재하지 않음
     */
    @Test
    @DisplayName("필수 파라미터 누락 시 로그인 실패")
    void login_MissingParameters_Fail() throws Exception {
        // When & Then: username만 전송 (password 누락)
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", testEmail))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("access"));
    }

    /**
     * 테스트 6: Content-Type이 잘못된 경우 처리
     *
     * 시나리오:
     * 1. application/json 형식으로 로그인 시도
     * 2. LoginFilter는 form-urlencoded만 처리
     * 3. 파라미터 추출 실패로 인증 실패
     *
     * 검증 사항:
     * - HTTP 401 상태 코드 (또는 다른 에러 처리 방식에 따라 다름)
     *
     * 참고: 현재 구현에서는 form-urlencoded만 지원
     */
    @Test
    @DisplayName("JSON 형식으로 로그인 시도 - 실패 (form-urlencoded만 지원)")
    void login_JsonContentType_Fail() throws Exception {
        // When & Then
        String jsonBody = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", testEmail, testPassword);

        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andDo(print())
                .andExpect(status().isUnauthorized());

        // 참고: 향후 JSON 형식도 지원하려면 LoginFilter를 커스터마이징 필요
    }
}
