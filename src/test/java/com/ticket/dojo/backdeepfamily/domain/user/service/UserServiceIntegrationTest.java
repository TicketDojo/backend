package com.ticket.dojo.backdeepfamily.domain.user.service;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User.Role;
import com.ticket.dojo.backdeepfamily.integration.base.BaseServiceIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserService 통합 테스트
 *
 * 테스트 범위:
 * - 실제 Spring Context 사용
 * - 실제 DB(MySQL) 사용
 * - UserService + UserRepository 통합 테스트
 *
 * 테스트 항목:
 * 1. 정상 회원가입
 * 2. 이메일 중복 시 처리
 * 3. 비밀번호 암호화 (BCrypt)
 * 4. 기본 Role 설정
 * 5. Email이 Name으로도 사용되는지
 */
@DisplayName("UserService 통합 테스트")
class UserServiceIntegrationTest extends BaseServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 - 정상적인 회원가입 성공 및 DB 저장 검증")
    void join_Success_SavedInDatabase() {
        // given
        String email = "test@example.com";
        String password = "password123";
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when
        userService.join(request);

        // then
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getPassword()).isNotEqualTo(password);  // 암호화되어야 함
    }

    @Test
    @DisplayName("회원가입 - 이메일 중복 시 회원가입 실패 (기존 사용자 유지)")
    void join_DuplicateEmail_DoesNotSave() {
        // given
        String email = "duplicate@example.com";
        String password = "password123";

        // 첫 번째 회원가입
        UserLoginRequest firstRequest = new UserLoginRequest();
        firstRequest.setEmail(email);
        firstRequest.setPassword(password);
        userService.join(firstRequest);

        long countAfterFirst = userRepository.count();
        assertThat(countAfterFirst).isEqualTo(1);

        // when: 동일한 이메일로 두 번째 회원가입 시도
        UserLoginRequest secondRequest = new UserLoginRequest();
        secondRequest.setEmail(email);
        secondRequest.setPassword("differentPassword");
        userService.join(secondRequest);

        // then: 여전히 1명만 존재해야 함 (중복 가입 실패)
        long countAfterSecond = userRepository.count();
        assertThat(countAfterSecond).isEqualTo(1);

        // 기존 사용자의 비밀번호가 유지되어야 함
        User existingUser = userRepository.findByEmail(email);
        assertThat(existingUser).isNotNull();
        assertThat(passwordEncoder.matches(password, existingUser.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("differentPassword", existingUser.getPassword())).isFalse();
    }

    @Test
    @DisplayName("회원가입 - 비밀번호 암호화 검증 (BCrypt)")
    void join_PasswordEncrypted_WithBCrypt() {
        // given
        String email = "encrypt@example.com";
        String plainPassword = "mySecretPassword";
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(email);
        request.setPassword(plainPassword);

        // when
        userService.join(request);

        // then
        User savedUser = userRepository.findByEmail(email);
        String encodedPassword = savedUser.getPassword();

        // 1. 평문과 암호문이 달라야 함
        assertThat(encodedPassword).isNotEqualTo(plainPassword);

        // 2. BCrypt 형식인지 확인 ($2a$로 시작)
        assertThat(encodedPassword).startsWith("$2a$");

        // 3. BCryptPasswordEncoder로 검증 가능해야 함
        assertThat(passwordEncoder.matches(plainPassword, encodedPassword)).isTrue();

        // 4. 잘못된 비밀번호는 매칭 실패
        assertThat(passwordEncoder.matches("wrongPassword", encodedPassword)).isFalse();
    }

    @Test
    @DisplayName("회원가입 - 기본 Role은 USER로 설정")
    void join_DefaultRole_IsUser() {
        // given
        String email = "role@example.com";
        String password = "password123";
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when
        userService.join(request);

        // then
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("회원가입 - Email이 Name으로도 사용되는지 검증")
    void join_EmailUsedAsName() {
        // given
        String email = "emailasname@example.com";
        String password = "password123";
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when
        userService.join(request);

        // then
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser.getName()).isEqualTo(email);
    }

    @Test
    @DisplayName("회원가입 - 여러 사용자 회원가입 시 각각 저장됨")
    void join_MultipleUsers_AllSaved() {
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

        // when
        userService.join(user1);
        userService.join(user2);
        userService.join(user3);

        // then
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(userRepository.findByEmail("user1@example.com")).isNotNull();
        assertThat(userRepository.findByEmail("user2@example.com")).isNotNull();
        assertThat(userRepository.findByEmail("user3@example.com")).isNotNull();
    }

    @Test
    @DisplayName("회원가입 - createdAt, updatedAt이 자동으로 설정됨")
    void join_TimestampsAutoSet() {
        // given
        String email = "timestamp@example.com";
        String password = "password123";
        UserLoginRequest request = new UserLoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when
        userService.join(request);

        // then
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
    }
}
