package com.ticket.dojo.backdeepfamily.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.RefreshToken;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.RefreshRepository;

/**
 * RefreshService 단위 테스트
 *
 * 테스트 목적:
 * - Refresh 토큰 관리 로직을 격리하여 테스트
 * - UUID 기반 refresh 토큰의 저장, 검증, 삭제 기능 확인
 *
 * 테스트 케이스:
 * 1. Refresh 토큰 저장 (신규 사용자)
 * 2. Refresh 토큰 저장 (기존 토큰 덮어쓰기)
 * 3. 유효한 Refresh 토큰 검증 - 성공
 * 4. 존재하지 않는 Refresh 토큰 검증 - 실패
 * 5. 만료된 Refresh 토큰 검증 - 실패 및 자동 삭제
 * 6. Refresh 토큰으로 이메일 조회 - 성공
 * 7. Refresh 토큰으로 이메일 조회 - 토큰 없음
 * 8. Refresh 토큰 삭제 (로그아웃)
 * 9. 이메일로 Refresh 토큰 삭제
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshService 단위 테스트")
class RefreshServiceTest {

    @InjectMocks
    private RefreshService refreshService;

    @Mock
    private RefreshRepository refreshRepository;

    private String testEmail;
    private String testRefreshToken;
    private LocalDateTime futureExpiration;
    private LocalDateTime pastExpiration;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testRefreshToken = "550e8400-e29b-41d4-a716-446655440000"; // UUID 형식
        futureExpiration = LocalDateTime.now().plusDays(1);
        pastExpiration = LocalDateTime.now().minusDays(1);
    }

    @Test
    @DisplayName("신규 사용자의 Refresh 토큰 저장 - 성공")
    void saveRefreshToken_NewUser_Success() {
        // Given: 기존 토큰이 없는 신규 사용자
        when(refreshRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // When: Refresh 토큰 저장
        refreshService.saveRefreshToken(testEmail, testRefreshToken, futureExpiration);

        // Then: 저장이 정상적으로 실행되었는지 확인
        verify(refreshRepository, times(1)).findByEmail(testEmail);
        verify(refreshRepository, times(1)).save(any(RefreshToken.class));

        // 저장된 토큰 검증
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getEmail()).isEqualTo(testEmail);
        assertThat(savedToken.getRefreshToken()).isEqualTo(testRefreshToken);
        assertThat(savedToken.getExpiration()).isEqualTo(futureExpiration);
    }

    @Test
    @DisplayName("기존 토큰이 있는 사용자의 Refresh 토큰 저장 - 기존 토큰 삭제 후 새 토큰 저장")
    void saveRefreshToken_ExistingUser_ReplaceToken() {
        // Given: 기존 토큰이 있는 사용자
        RefreshToken oldToken = RefreshToken.builder()
                .email(testEmail)
                .refreshToken("old-token-uuid")
                .expiration(futureExpiration)
                .build();
        when(refreshRepository.findByEmail(testEmail)).thenReturn(Optional.of(oldToken));

        // When: 새로운 Refresh 토큰 저장
        refreshService.saveRefreshToken(testEmail, testRefreshToken, futureExpiration);

        // Then: 기존 토큰 삭제 후 새 토큰 저장
        verify(refreshRepository, times(1)).delete(oldToken);
        verify(refreshRepository, times(1)).save(any(RefreshToken.class));

        // 저장된 토큰이 새 토큰인지 확인
        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshRepository).save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getRefreshToken()).isEqualTo(testRefreshToken);
        assertThat(savedToken.getRefreshToken()).isNotEqualTo("old-token-uuid");
    }

    @Test
    @DisplayName("유효한 Refresh 토큰 검증 - 성공")
    void validateRefreshToken_ValidToken_Success() {
        // Given: 유효한 토큰 (만료 전)
        RefreshToken validToken = RefreshToken.builder()
                .email(testEmail)
                .refreshToken(testRefreshToken)
                .expiration(futureExpiration)
                .build();
        when(refreshRepository.findByRefreshToken(testRefreshToken)).thenReturn(Optional.of(validToken));

        // When: 토큰 검증
        boolean result = refreshService.validateRefreshToken(testRefreshToken);

        // Then: 검증 성공
        assertThat(result).isTrue();
        verify(refreshRepository, times(1)).findByRefreshToken(testRefreshToken);
    }

    @Test
    @DisplayName("존재하지 않는 Refresh 토큰 검증 - 실패")
    void validateRefreshToken_TokenNotFound_Fail() {
        // Given: 토큰이 DB에 없음
        when(refreshRepository.findByRefreshToken(testRefreshToken)).thenReturn(Optional.empty());

        // When: 토큰 검증
        boolean result = refreshService.validateRefreshToken(testRefreshToken);

        // Then: 검증 실패
        assertThat(result).isFalse();
        verify(refreshRepository, times(1)).findByRefreshToken(testRefreshToken);
    }

    @Test
    @DisplayName("만료된 Refresh 토큰 검증 - 실패 및 자동 삭제")
    void validateRefreshToken_ExpiredToken_FailAndDelete() {
        // Given: 만료된 토큰
        RefreshToken expiredToken = RefreshToken.builder()
                .email(testEmail)
                .refreshToken(testRefreshToken)
                .expiration(pastExpiration)
                .build();
        when(refreshRepository.findByRefreshToken(testRefreshToken)).thenReturn(Optional.of(expiredToken));

        // When: 토큰 검증
        boolean result = refreshService.validateRefreshToken(testRefreshToken);

        // Then: 검증 실패 + 자동 삭제
        assertThat(result).isFalse();
        verify(refreshRepository, times(1)).findByRefreshToken(testRefreshToken);
        verify(refreshRepository, times(1)).delete(expiredToken);
    }

    @Test
    @DisplayName("Refresh 토큰으로 이메일 조회 - 성공")
    void getEmailByRefreshToken_TokenExists_Success() {
        // Given: 토큰이 DB에 존재
        RefreshToken token = RefreshToken.builder()
                .email(testEmail)
                .refreshToken(testRefreshToken)
                .expiration(futureExpiration)
                .build();
        when(refreshRepository.findByRefreshToken(testRefreshToken)).thenReturn(Optional.of(token));

        // When: 이메일 조회
        Optional<String> result = refreshService.getEmailByRefreshToken(testRefreshToken);

        // Then: 이메일 반환
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testEmail);
        verify(refreshRepository, times(1)).findByRefreshToken(testRefreshToken);
    }

    @Test
    @DisplayName("Refresh 토큰으로 이메일 조회 - 토큰 없음")
    void getEmailByRefreshToken_TokenNotFound_Empty() {
        // Given: 토큰이 DB에 없음
        when(refreshRepository.findByRefreshToken(testRefreshToken)).thenReturn(Optional.empty());

        // When: 이메일 조회
        Optional<String> result = refreshService.getEmailByRefreshToken(testRefreshToken);

        // Then: 빈 Optional 반환
        assertThat(result).isEmpty();
        verify(refreshRepository, times(1)).findByRefreshToken(testRefreshToken);
    }

    @Test
    @DisplayName("Refresh 토큰 삭제 (로그아웃)")
    void deleteRefreshToken_Success() {
        // When: 토큰 삭제
        refreshService.deleteRefreshToken(testRefreshToken);

        // Then: 삭제 메서드 호출 확인
        verify(refreshRepository, times(1)).deleteByRefreshToken(testRefreshToken);
    }

    @Test
    @DisplayName("이메일로 Refresh 토큰 삭제")
    void deleteRefreshTokenByEmail_Success() {
        // When: 이메일로 토큰 삭제
        refreshService.deleteRefreshTokenByEmail(testEmail);

        // Then: 삭제 메서드 호출 확인
        verify(refreshRepository, times(1)).deleteByEmail(testEmail);
    }
}
