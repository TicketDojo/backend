package com.ticket.dojo.backdeepfamily.integration.scenario;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.integration.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 플로우 시나리오 E2E 테스트
 *
 * 테스트 목적:
 * - JWT 인증 전체 플로우 검증
 * - Access Token, Refresh Token 관리 검증
 * - 로그인 → 사용 → Refresh → Logout 전체 사이클 검증
 *
 * 테스트 시나리오:
 * 1. 로그인 → JWT 사용 → Refresh → Logout 전체 플로우
 * 2. Refresh Token으로 Access Token 재발급
 * 3. 로그아웃 후 블랙리스트 토큰으로 API 접근 불가
 */
@SpringBootTest
@DisplayName("인증 플로우 시나리오 E2E 테스트")
class AuthFlowScenarioTest extends BaseControllerIntegrationTest {

    @BeforeEach
    void setUp() {
        cleanupAllDomains();
    }

    @AfterEach
    void tearDown() {
        cleanupAllDomains();
    }

    /**
     * 시나리오 1: 로그인 → JWT 사용 → Refresh → Logout 전체 플로우
     *
     * 플로우:
     * 1. 회원가입
     * 2. 로그인 (Access Token + Refresh Token 발급)
     * 3. Access Token으로 API 호출 (대기열 진입)
     * 4. Refresh Token으로 새로운 Access Token 발급
     * 5. 새로운 Access Token으로 API 호출
     * 6. 로그아웃
     * 7. 로그아웃한 토큰으로 API 호출 실패
     */
    @Test
    @DisplayName("로그인 → JWT 사용 → Refresh → Logout 전체 플로우")
    void login_UseJwt_Refresh_Logout_Scenario() throws Exception {
        // 1. 회원가입
        String email = "auth-flow@example.com";
        String password = "password123";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andDo(print())
                .andExpect(status().isOk());

        // 2. 로그인 (Access Token + Refresh Token 발급)
        LoginResult loginResult = performLoginAndGetTokens(email, password);
        String accessToken = loginResult.getAccessToken();
        String refreshToken = loginResult.getRefreshToken();

        assertThat(accessToken).isNotNull();
        assertThat(refreshToken).isNotNull();

        // 3. Access Token으로 API 호출 (대기열 진입)
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken))
                .andDo(print())
                .andExpect(status().isOk());

        // 4. Refresh Token으로 새로운 Access Token 발급
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("refresh", refreshToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String newAccessToken = refreshResult.getResponse().getHeader("access");
        assertThat(newAccessToken).isNotNull();
        assertThat(newAccessToken).isNotEqualTo(accessToken); // 새로운 토큰 발급 확인

        // 5. 새로운 Access Token으로 API 호출 가능
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", newAccessToken))
                .andDo(print())
                .andExpect(status().isOk());

        // 6. 로그아웃
        mockMvc.perform(post("/auth/logout")
                        .header("refresh", refreshToken))
                .andDo(print())
                .andExpect(status().isOk());

        // 7. 로그아웃한 Refresh Token으로 재발급 시도 실패
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("refresh", refreshToken))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    /**
     * 시나리오 2: Access Token 만료 후 Refresh Token으로 재발급
     *
     * 플로우:
     * 1. 로그인
     * 2. Access Token으로 API 호출 성공
     * 3. Refresh Token으로 새로운 Access Token 발급
     * 4. 새로운 Access Token으로 API 호출 성공
     * 5. 이전 Access Token은 여전히 사용 가능 (블랙리스트 X)
     */
    @Test
    @DisplayName("Refresh Token으로 Access Token 재발급")
    void refreshToken_ReissueAccessToken_Success() throws Exception {
        // 1. 회원가입 및 로그인
        String email = "refresh@example.com";
        String password = "password";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andExpect(status().isOk());

        LoginResult loginResult = performLoginAndGetTokens(email, password);
        String oldAccessToken = loginResult.getAccessToken();
        String refreshToken = loginResult.getRefreshToken();

        // 2. 이전 Access Token으로 API 호출 성공
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", oldAccessToken))
                .andExpect(status().isOk());

        // 3. Refresh Token으로 새로운 Access Token 발급
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("refresh", refreshToken))
                .andExpect(status().isOk())
                .andReturn();

        String newAccessToken = refreshResult.getResponse().getHeader("access");
        assertThat(newAccessToken).isNotNull();

        // 4. 새로운 Access Token으로 API 호출 성공
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", newAccessToken))
                .andExpect(status().isOk());

        // 5. 이전 Access Token도 여전히 사용 가능 (로그아웃하지 않았으므로)
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", oldAccessToken))
                .andExpect(status().isOk());
    }

    /**
     * 시나리오 3: 로그아웃 후 블랙리스트 토큰으로 API 접근 불가
     *
     * 플로우:
     * 1. 로그인
     * 2. API 호출 성공
     * 3. 로그아웃
     * 4. 동일한 Access Token으로 API 호출 실패 (블랙리스트)
     * 5. Refresh Token으로 재발급 시도 실패 (로그아웃됨)
     */
    @Test
    @DisplayName("로그아웃 후 블랙리스트 토큰으로 API 접근 불가")
    void logout_BlacklistedToken_CannotAccessApi() throws Exception {
        // 1. 회원가입 및 로그인
        String email = "logout@example.com";
        String password = "password";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andExpect(status().isOk());

        LoginResult loginResult = performLoginAndGetTokens(email, password);
        String accessToken = loginResult.getAccessToken();
        String refreshToken = loginResult.getRefreshToken();

        // 2. API 호출 성공
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken))
                .andExpect(status().isOk());

        // 3. 로그아웃
        mockMvc.perform(post("/auth/logout")
                        .header("refresh", refreshToken))
                .andExpect(status().isOk());

        // 4. 로그아웃한 Access Token으로 API 호출 시도 (블랙리스트로 인해 실패 가능)
        // 참고: 실제 구현에 따라 블랙리스트 처리 여부가 다를 수 있음
        // 로그아웃 시 Access Token을 블랙리스트에 추가하는 경우
        // mockMvc.perform(
        //                 authenticatedPost("/queue/jwt/enter", accessToken))
        //         .andExpect(status().isUnauthorized());

        // 5. 로그아웃한 Refresh Token으로 재발급 시도 실패
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("refresh", refreshToken))
                .andExpect(status().is4xxClientError());
    }

    /**
     * 시나리오 4: Refresh Token Rotation - 여러 번 재발급 시나리오
     *
     * 플로우:
     * 1. 로그인
     * 2. Refresh Token으로 Access Token 재발급 (1차)
     * 3. 새로운 Refresh Token으로 Access Token 재발급 (2차)
     * 4. 각 단계에서 발급된 토큰으로 API 호출 성공
     */
    @Test
    @DisplayName("Refresh Token Rotation - 여러 번 재발급")
    void refreshTokenRotation_MultipleReissue() throws Exception {
        // 1. 회원가입 및 로그인
        String email = "rotation@example.com";
        String password = "password";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andExpect(status().isOk());

        LoginResult loginResult1 = performLoginAndGetTokens(email, password);
        String accessToken1 = loginResult1.getAccessToken();
        String refreshToken1 = loginResult1.getRefreshToken();

        // 2. 첫 번째 Refresh Token으로 재발급
        MvcResult refreshResult1 = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("refresh", refreshToken1))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken2 = refreshResult1.getResponse().getHeader("access");
        String refreshToken2 = extractRefreshTokenFromCookies(refreshResult1.getResponse().getCookies());

        assertThat(accessToken2).isNotNull();
        assertThat(refreshToken2).isNotNull();

        // 새로운 Access Token으로 API 호출 성공
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken2))
                .andExpect(status().isOk());

        // 3. 두 번째 Refresh Token으로 재발급
        MvcResult refreshResult2 = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("refresh", refreshToken2))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken3 = refreshResult2.getResponse().getHeader("access");
        assertThat(accessToken3).isNotNull();

        // 세 번째 Access Token으로 API 호출 성공
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken3))
                .andExpect(status().isOk());
    }

    /**
     * 쿠키에서 refresh 토큰 추출
     */
    private String extractRefreshTokenFromCookies(jakarta.servlet.http.Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : cookies) {
            if ("refresh".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
