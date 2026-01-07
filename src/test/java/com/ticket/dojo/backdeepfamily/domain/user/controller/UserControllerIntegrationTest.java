package com.ticket.dojo.backdeepfamily.domain.user.controller;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.integration.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 통합 테스트
 *
 * 테스트 범위:
 * - Controller → Service → Repository 전체 흐름
 * - HTTP 요청/응답 검증
 * - Spring Security 통합 (Public endpoint 테스트)
 *
 * 테스트 항목:
 * 1. 정상 회원가입
 * 2. 이메일 중복 시 처리
 * 3. 회원가입 후 바로 로그인
 * 4. 여러 사용자 연속 회원가입
 */
@DisplayName("UserController 통합 테스트")
class UserControllerIntegrationTest extends BaseControllerIntegrationTest {

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 API - POST /users - 정상적인 회원가입 성공")
    void join_WithValidData_Returns200() throws Exception {
        // given
        String email = "newuser@example.com";
        String password = "password123";

        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when & then
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        // DB 검증
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(passwordEncoder.matches(password, savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("회원가입 API - 이메일 중복 시 처리 (여전히 200 OK)")
    void join_WithDuplicateEmail_Returns200() throws Exception {
        // given: 첫 번째 사용자 회원가입
        String email = "duplicate@example.com";
        String firstPassword = "password123";
        String secondPassword = "differentPassword";

        UserLoginRequest firstRequest = new UserLoginRequest();
        firstRequest.setEmail(email);
        firstRequest.setPassword(firstPassword);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(firstRequest)))
                .andExpect(status().isOk());

        long countAfterFirst = userRepository.count();
        assertThat(countAfterFirst).isEqualTo(1);

        // when: 동일한 이메일로 두 번째 회원가입 시도
        UserLoginRequest secondRequest = new UserLoginRequest();
        secondRequest.setEmail(email);
        secondRequest.setPassword(secondPassword);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(secondRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        // then: 여전히 1명만 존재 (중복 가입 실패)
        long countAfterSecond = userRepository.count();
        assertThat(countAfterSecond).isEqualTo(1);

        // 기존 비밀번호 유지 확인
        User existingUser = userRepository.findByEmail(email);
        assertThat(existingUser).isNotNull();
        assertThat(passwordEncoder.matches(firstPassword, existingUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(secondPassword, existingUser.getPassword())).isFalse();
    }

    @Test
    @DisplayName("회원가입 후 바로 로그인 - 시나리오 테스트")
    void join_ThenLogin_Scenario() throws Exception {
        // given: 회원가입
        String email = "logintest@example.com";
        String password = "password123";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andExpect(status().isOk());

        // when: 회원가입 후 바로 로그인
        String accessToken = performLoginAndGetAccessToken(email, password);

        // then: 로그인 성공 (토큰 발급됨)
        assertThat(accessToken).isNotNull();
        assertThat(accessToken).isNotEmpty();
    }

    @Test
    @DisplayName("회원가입 API - 여러 사용자 연속 회원가입")
    void join_MultipleUsers_AllSucceed() throws Exception {
        // given
        UserLoginRequest user1 = new UserLoginRequest();
        user1.setEmail("user1@example.com");
        user1.setPassword("password1");

        UserLoginRequest user2 = new UserLoginRequest();
        user2.setEmail("user2@example.com");
        user2.setPassword("password2");

        UserLoginRequest user3 = new UserLoginRequest();
        user3.setEmail("user3@example.com");
        user3.setPassword("password3");

        // when & then
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(user1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(user2)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(user3)))
                .andExpect(status().isOk());

        // DB 검증
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(userRepository.findByEmail("user1@example.com")).isNotNull();
        assertThat(userRepository.findByEmail("user2@example.com")).isNotNull();
        assertThat(userRepository.findByEmail("user3@example.com")).isNotNull();
    }

    @Test
    @DisplayName("회원가입 API - JSON 형식으로 요청 성공")
    void join_WithJsonContentType_Success() throws Exception {
        // given
        String email = "jsonuser@example.com";
        String password = "password123";
        String jsonBody = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);

        // when & then
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        // DB 검증
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("회원가입 API - 한글 이메일도 저장 가능")
    void join_WithKoreanName_Success() throws Exception {
        // given
        String email = "한글테스트@example.com";
        String password = "password123";

        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when & then
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk());

        // DB 검증
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(email);
    }
}
