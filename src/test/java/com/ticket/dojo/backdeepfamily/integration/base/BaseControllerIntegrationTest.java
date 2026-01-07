package com.ticket.dojo.backdeepfamily.integration.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 통합 테스트를 위한 Base 클래스
 *
 * 목적:
 * - MockMvc 설정 제공
 * - JWT 인증 헬퍼 메서드 제공
 * - Authenticated request builders 제공
 * - JSON serialization 유틸리티
 *
 * 제공 기능:
 * 1. performLoginAndGetAccessToken() - 로그인 후 access 토큰 반환
 * 2. performLoginAndGetRefreshToken() - 로그인 후 refresh 토큰 반환
 * 3. authenticatedGet/Post/Put/Delete() - 인증된 요청 빌더
 * 4. toJson() - 객체를 JSON 문자열로 변환
 *
 * 중요사항:
 * - JWT 토큰은 "access" 헤더로 전달 (Authorization 헤더 아님!)
 * - 로그인은 application/x-www-form-urlencoded 형식 사용
 * - refresh 토큰은 쿠키로 전달됨
 */
@AutoConfigureMockMvc
public abstract class BaseControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 로그인하여 Access Token 발급받기
     *
     * @param email    사용자 이메일
     * @param password 사용자 비밀번호 (평문)
     * @return Access Token (JWT)
     * @throws Exception 로그인 실패 시
     */
    protected String performLoginAndGetAccessToken(String email, String password) throws Exception {
        String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andReturn();

        return result.getResponse().getHeader("access");
    }

    /**
     * 로그인하여 Refresh Token 발급받기
     *
     * @param email    사용자 이메일
     * @param password 사용자 비밀번호 (평문)
     * @return Refresh Token (JWT) - 쿠키에서 추출
     * @throws Exception 로그인 실패 시
     */
    protected String performLoginAndGetRefreshToken(String email, String password) throws Exception {
        String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andReturn();

        // 쿠키에서 refresh 토큰 추출
        Cookie[] cookies = result.getResponse().getCookies();
        for (Cookie cookie : cookies) {
            if ("refresh".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 로그인하여 Access Token과 Refresh Token 모두 발급받기
     *
     * @param email    사용자 이메일
     * @param password 사용자 비밀번호 (평문)
     * @return LoginResult (access token + refresh token)
     * @throws Exception 로그인 실패 시
     */
    protected LoginResult performLoginAndGetTokens(String email, String password) throws Exception {
        String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = result.getResponse().getHeader("access");
        String refreshToken = extractRefreshTokenFromCookies(result.getResponse().getCookies());

        return new LoginResult(accessToken, refreshToken);
    }

    /**
     * 쿠키에서 refresh 토큰 추출
     */
    private String extractRefreshTokenFromCookies(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("refresh".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * GET 요청 빌더 (JWT 인증 포함)
     *
     * @param url         요청 URL
     * @param accessToken Access Token
     * @return MockHttpServletRequestBuilder
     */
    protected MockHttpServletRequestBuilder authenticatedGet(String url, String accessToken) {
        return get(url).header("access", accessToken);
    }

    /**
     * POST 요청 빌더 (JWT 인증 포함)
     *
     * @param url         요청 URL
     * @param accessToken Access Token
     * @return MockHttpServletRequestBuilder
     */
    protected MockHttpServletRequestBuilder authenticatedPost(String url, String accessToken) {
        return post(url).header("access", accessToken);
    }

    /**
     * PUT 요청 빌더 (JWT 인증 포함)
     *
     * @param url         요청 URL
     * @param accessToken Access Token
     * @return MockHttpServletRequestBuilder
     */
    protected MockHttpServletRequestBuilder authenticatedPut(String url, String accessToken) {
        return put(url).header("access", accessToken);
    }

    /**
     * DELETE 요청 빌더 (JWT 인증 포함)
     *
     * @param url         요청 URL
     * @param accessToken Access Token
     * @return MockHttpServletRequestBuilder
     */
    protected MockHttpServletRequestBuilder authenticatedDelete(String url, String accessToken) {
        return delete(url).header("access", accessToken);
    }

    /**
     * 객체를 JSON 문자열로 변환
     *
     * @param obj 변환할 객체
     * @return JSON 문자열
     * @throws JsonProcessingException JSON 변환 실패 시
     */
    protected String toJson(Object obj) throws JsonProcessingException {
        return objectMapper.writeValueAsString(obj);
    }

    /**
     * 로그인 결과를 담는 DTO
     */
    protected static class LoginResult {
        private final String accessToken;
        private final String refreshToken;

        public LoginResult(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }
    }
}
