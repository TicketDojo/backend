package com.ticket.dojo.backdeepfamily.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

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

import com.ticket.dojo.backdeepfamily.domain.auth.repository.BlackListRepository;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;
import com.ticket.dojo.backdeepfamily.domain.auth.service.RefreshService;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User.Role;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.util.jwt.JWTUtil;

import jakarta.servlet.http.Cookie;

/**
 * AuthController 통합 테스트
 *
 * 테스트 목적:
 * - 토큰 재발급(/auth/refresh) 및 로그아웃(/auth/logout) API 검증
 * - UUID 기반 refresh 토큰과 블랙리스트 기능 통합 테스트
 *
 * 테스트 케이스:
 * 1. 유효한 Refresh 토큰으로 Access 토큰 재발급 - 성공
 * 2. Refresh 토큰 없이 재발급 시도 - 실패
 * 3. 만료된 Refresh 토큰으로 재발급 시도 - 실패
 * 4. 존재하지 않는 Refresh 토큰으로 재발급 시도 - 실패
 * 5. 정상 로그아웃 - Access 토큰 블랙리스트 추가 및 Refresh 토큰 삭제
 * 6. 로그아웃 후 블랙리스트된 Access 토큰 사용 불가 확인
 * 7. 로그아웃 후 Refresh 토큰 사용 불가 확인
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AuthController 통합 테스트 (Refresh & Logout)")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshRepository refreshRepository;

    @Autowired
    private BlackListRepository blackListRepository;

    @Autowired
    private RefreshService refreshService;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private JWTUtil jwtUtil;

    private String testEmail;
    private String testPassword;
    private User testUser;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testPassword = "password123";

        // 테스트용 사용자 생성
        testUser = User.builder()
                .email(testEmail)
                .name(testEmail)
                .password(bCryptPasswordEncoder.encode(testPassword))
                .role(Role.USER)
                .build();

        userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        // 테스트 데이터 정리
        blackListRepository.deleteAll();
        refreshRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * 로그인하여 access 토큰과 refresh 토큰을 발급받는 헬퍼 메서드
     */
    private MvcResult performLogin() throws Exception {
        String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", testEmail, testPassword);
        return mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk())
                .andReturn();
    }

    /**
     * 쿠키에서 refresh 토큰을 추출하는 헬퍼 메서드
     */
    private String extractRefreshToken(MvcResult result) {
        Cookie[] cookies = result.getResponse().getCookies();
        for (Cookie cookie : cookies) {
            if ("refresh".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 헤더에서 access 토큰을 추출하는 헬퍼 메서드
     */
    private String extractAccessToken(MvcResult result) {
        String accessHeader = result.getResponse().getHeader("access");
        return accessHeader;
    }

    @Test
    @DisplayName("유효한 Refresh 토큰으로 Access 토큰 재발급 - 성공")
    void reissue_ValidRefreshToken_Success() throws Exception {
        // Given: 로그인하여 토큰 발급
        MvcResult loginResult = performLogin();
        String refreshToken = extractRefreshToken(loginResult);

        assertThat(refreshToken).isNotNull();

        // When: 재발급 요청
        MvcResult reissueResult = mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie("refresh", refreshToken)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().exists("access"))  // 새로운 access 토큰 발급
                .andReturn();

        // Then: 새로운 토큰 검증
        String newAccessToken = extractAccessToken(reissueResult);
        String newRefreshToken = extractRefreshToken(reissueResult);

        assertThat(newAccessToken).isNotNull();
        assertThat(newRefreshToken).isNotNull();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);  // Refresh Token Rotation

        // JWT 토큰 내용 검증
        String username = jwtUtil.getUsername(newAccessToken);
        assertThat(username).isEqualTo(testEmail);

        // 기존 refresh 토큰은 DB에서 삭제되었는지 확인
        boolean oldTokenExists = refreshService.validateRefreshToken(refreshToken);
        assertThat(oldTokenExists).isFalse();

        // 새 refresh 토큰은 DB에 존재하는지 확인
        boolean newTokenExists = refreshService.validateRefreshToken(newRefreshToken);
        assertThat(newTokenExists).isTrue();
    }

    @Test
    @DisplayName("Refresh 토큰 없이 재발급 시도 - 401 에러")
    void reissue_NoRefreshToken_Fail() throws Exception {
        // When & Then: refresh 토큰 없이 재발급 시도
        mockMvc.perform(post("/auth/refresh"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("만료된 Refresh 토큰으로 재발급 시도 - 401 에러")
    void reissue_ExpiredRefreshToken_Fail() throws Exception {
        // Given: 만료된 refresh 토큰을 DB에 저장
        String expiredToken = UUID.randomUUID().toString();
        LocalDateTime pastExpiration = LocalDateTime.now().minusDays(1);
        refreshService.saveRefreshToken(testEmail, expiredToken, pastExpiration);

        // When & Then: 만료된 토큰으로 재발급 시도
        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie("refresh", expiredToken)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 Refresh 토큰으로 재발급 시도 - 401 에러")
    void reissue_InvalidRefreshToken_Fail() throws Exception {
        // Given: DB에 없는 랜덤 UUID
        String invalidToken = UUID.randomUUID().toString();

        // When & Then: 존재하지 않는 토큰으로 재발급 시도
        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie("refresh", invalidToken)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상 로그아웃 - Access 토큰 블랙리스트 추가 및 Refresh 토큰 삭제")
    void logout_Success() throws Exception {
        // Given: 로그인하여 토큰 발급
        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);
        String refreshToken = extractRefreshToken(loginResult);

        assertThat(accessToken).isNotNull();
        assertThat(refreshToken).isNotNull();

        // When: 로그아웃 요청
        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .cookie(new Cookie("refresh", refreshToken)))
                .andDo(print())
                .andExpect(status().isOk());

        // Then: Refresh 토큰이 DB에서 삭제되었는지 확인
        boolean refreshTokenExists = refreshService.validateRefreshToken(refreshToken);
        assertThat(refreshTokenExists).isFalse();

        // Access 토큰이 블랙리스트에 추가되었는지 확인
        boolean isBlacklisted = blackListRepository.existsByAccessToken(accessToken);
        assertThat(isBlacklisted).isTrue();
    }

    @Test
    @DisplayName("로그아웃 후 블랙리스트된 Access 토큰 사용 불가 확인")
    void logout_BlacklistedTokenCannotBeUsed() throws Exception {
        // Given: 로그인 후 로그아웃
        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);
        String refreshToken = extractRefreshToken(loginResult);

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .cookie(new Cookie("refresh", refreshToken)))
                .andExpect(status().isOk());

        // When & Then: 블랙리스트된 토큰으로 보호된 API 호출 시도
        // 블랙리스트 확인
        boolean isBlacklisted = blackListRepository.existsByAccessToken(accessToken);
        assertThat(isBlacklisted).isTrue();

        // 참고: 실제 보호된 엔드포인트로 테스트하려면 해당 엔드포인트가 필요
        // 예시: mockMvc.perform(get("/api/protected").header("Authorization", "Bearer " + accessToken))
        //         .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그아웃 후 Refresh 토큰 사용 불가 확인")
    void logout_RefreshTokenCannotBeUsed() throws Exception {
        // Given: 로그인 후 로그아웃
        MvcResult loginResult = performLogin();
        String accessToken = extractAccessToken(loginResult);
        String refreshToken = extractRefreshToken(loginResult);

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .cookie(new Cookie("refresh", refreshToken)))
                .andExpect(status().isOk());

        // When & Then: 로그아웃된 refresh 토큰으로 재발급 시도
        mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie("refresh", refreshToken)))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access 토큰 없이 로그아웃 시도 - 성공 (Refresh만 삭제)")
    void logout_NoAccessToken_Success() throws Exception {
        // Given: 로그인하여 refresh 토큰만 사용
        MvcResult loginResult = performLogin();
        String refreshToken = extractRefreshToken(loginResult);

        // When: Access 토큰 없이 로그아웃
        mockMvc.perform(post("/auth/logout")
                .cookie(new Cookie("refresh", refreshToken)))
                .andDo(print())
                .andExpect(status().isOk());

        // Then: Refresh 토큰만 삭제되었는지 확인
        boolean refreshTokenExists = refreshService.validateRefreshToken(refreshToken);
        assertThat(refreshTokenExists).isFalse();
    }

    @Test
    @DisplayName("Refresh Token Rotation 검증 - 재발급 시 새로운 UUID 생성")
    void reissue_RefreshTokenRotation_NewUUID() throws Exception {
        // Given: 로그인하여 토큰 발급
        MvcResult loginResult = performLogin();
        String oldRefreshToken = extractRefreshToken(loginResult);

        // When: 첫 번째 재발급
        MvcResult reissue1 = mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie("refresh", oldRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken1 = extractRefreshToken(reissue1);

        // Then: 두 번째 재발급
        MvcResult reissue2 = mockMvc.perform(post("/auth/refresh")
                .cookie(new Cookie("refresh", refreshToken1)))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken2 = extractRefreshToken(reissue2);

        // 모든 토큰이 서로 다른 UUID인지 확인
        assertThat(oldRefreshToken).isNotEqualTo(refreshToken1);
        assertThat(refreshToken1).isNotEqualTo(refreshToken2);
        assertThat(oldRefreshToken).isNotEqualTo(refreshToken2);

        // 최신 토큰만 유효한지 확인
        assertThat(refreshService.validateRefreshToken(oldRefreshToken)).isFalse();
        assertThat(refreshService.validateRefreshToken(refreshToken1)).isFalse();
        assertThat(refreshService.validateRefreshToken(refreshToken2)).isTrue();
    }
}
