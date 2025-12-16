package com.ticket.dojo.backdeepfamily.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User.Role;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;

/**
 * UserServiceImpl 단위 테스트 (Mockito 사용)
 *
 * 테스트 목적:
 * - UserService의 회원가입 비즈니스 로직만 격리하여 테스트
 * - 데이터베이스나 Spring Context 없이 빠르게 테스트
 *
 * Mock 객체 사용:
 * - UserRepository: 데이터베이스 접근을 모킹
 * - BCryptPasswordEncoder: 비밀번호 암호화를 모킹
 *
 * 테스트 케이스:
 * 1. 정상적인 회원가입
 * 2. 이미 존재하는 이메일로 가입 시도
 * 3. 비밀번호 암호화 검증
 * 4. 기본 권한(ROLE_USER) 설정 확인
 * 5. Email을 Name으로도 사용하는지 확인
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("회원가입 단위 테스트 (Mockito)")
class UserServiceImplTest {

    /**
     * 테스트 대상 서비스
     * @InjectMocks: Mock 객체들이 자동으로 주입됨
     */
    @InjectMocks
    private UserServiceImpl userService;

    /**
     * Mock 객체들
     * @Mock: 실제 객체 대신 가짜 객체를 생성
     */
    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    // 테스트용 데이터
    private UserLoginRequest validRequest;
    private String testEmail;
    private String testPassword;
    private String encodedPassword;

    /**
     * 각 테스트 실행 전 초기화
     */
    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testPassword = "password123";
        encodedPassword = "$2a$10$encodedPasswordHash";

        validRequest = new UserLoginRequest();
        validRequest.setEmail(testEmail);
        validRequest.setPassword(testPassword);
    }

    @Test
    @DisplayName("정상적인 회원가입 - 성공")
    void join_Success() {
        // Given: 테스트 데이터 준비
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);
        when(bCryptPasswordEncoder.encode(testPassword)).thenReturn(encodedPassword);

        // When: 회원가입 실행
        userService.join(validRequest);

        // Then: 검증
        verify(userRepository, times(1)).existsByEmail(testEmail);
        verify(bCryptPasswordEncoder, times(1)).encode(testPassword);
        verify(userRepository, times(1)).save(any(User.class));

        // save에 전달된 User 객체 검증
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo(testEmail);
        assertThat(savedUser.getName()).isEqualTo(testEmail);
        assertThat(savedUser.getPassword()).isEqualTo(encodedPassword);
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 회원가입 시도 - 실패")
    void join_EmailAlreadyExists_Fail() {
        // Given: 이메일이 이미 존재
        when(userRepository.existsByEmail(testEmail)).thenReturn(true);

        // When: 회원가입 시도
        userService.join(validRequest);

        // Then: 저장이 실행되지 않아야 함
        verify(userRepository, times(1)).existsByEmail(testEmail);
        verify(bCryptPasswordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("비밀번호 암호화 검증")
    void join_PasswordEncryption() {
        // Given
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);
        when(bCryptPasswordEncoder.encode(testPassword)).thenReturn(encodedPassword);

        // When
        userService.join(validRequest);

        // Then: 암호화된 비밀번호가 저장되었는지 확인
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getPassword()).isEqualTo(encodedPassword);
        assertThat(savedUser.getPassword()).isNotEqualTo(testPassword);
    }

    @Test
    @DisplayName("회원가입 시 기본 권한 USER 설정 확인")
    void join_DefaultRoleIsUser() {
        // Given
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);
        when(bCryptPasswordEncoder.encode(testPassword)).thenReturn(encodedPassword);

        // When
        userService.join(validRequest);

        // Then: 저장된 User의 role이 USER인지 확인
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("Email을 Name으로도 사용하는지 확인")
    void join_EmailUsedAsName() {
        // Given
        when(userRepository.existsByEmail(testEmail)).thenReturn(false);
        when(bCryptPasswordEncoder.encode(testPassword)).thenReturn(encodedPassword);

        // When
        userService.join(validRequest);

        // Then: email과 name이 동일한지 확인
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo(savedUser.getName());
        assertThat(savedUser.getName()).isEqualTo(testEmail);
    }
}
