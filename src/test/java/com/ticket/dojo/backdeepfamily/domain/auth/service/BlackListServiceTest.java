package com.ticket.dojo.backdeepfamily.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.BlackListToken;
import com.ticket.dojo.backdeepfamily.domain.auth.repository.BlackListRepository;

/**
 * BlackListService 단위 테스트
 *
 * 테스트 목적:
 * - 로그아웃 시 토큰 블랙리스트 관리 로직 테스트
 * - Access 토큰의 즉시 무효화 기능 확인
 *
 * 테스트 케이스:
 * 1. 블랙리스트에 토큰 추가 - 성공
 * 2. 블랙리스트에 있는 토큰 확인 - True
 * 3. 블랙리스트에 없는 토큰 확인 - False
 * 4. 만료된 블랙리스트 토큰 정리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BlackListService 단위 테스트")
class BlackListServiceTest {

    @InjectMocks
    private BlackListService blackListService;

    @Mock
    private BlackListRepository blackListRepository;

    private String testEmail;
    private String testAccessToken;
    private LocalDateTime futureExpiration;
    private LocalDateTime pastExpiration;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.testtoken.signature";
        futureExpiration = LocalDateTime.now().plusMinutes(10);
        pastExpiration = LocalDateTime.now().minusMinutes(10);
    }

    @Test
    @DisplayName("블랙리스트에 토큰 추가 - 성공")
    void addToBlacklist_Success() {
        // When: 블랙리스트에 토큰 추가
        blackListService.addToBlacklist(testEmail, testAccessToken, futureExpiration);

        // Then: 저장 메서드 호출 확인
        verify(blackListRepository, times(1)).save(any(BlackListToken.class));

        // 저장된 토큰 검증
        ArgumentCaptor<BlackListToken> tokenCaptor = ArgumentCaptor.forClass(BlackListToken.class);
        verify(blackListRepository).save(tokenCaptor.capture());
        BlackListToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getEmail()).isEqualTo(testEmail);
        assertThat(savedToken.getAccessToken()).isEqualTo(testAccessToken);
        assertThat(savedToken.getExpiration()).isEqualTo(futureExpiration);
    }

    @Test
    @DisplayName("블랙리스트에 있는 토큰 확인 - True")
    void isBlacklisted_TokenExists_ReturnTrue() {
        // Given: 토큰이 블랙리스트에 존재
        when(blackListRepository.existsByAccessToken(testAccessToken)).thenReturn(true);

        // When: 블랙리스트 확인
        boolean result = blackListService.isBlacklisted(testAccessToken);

        // Then: True 반환
        assertThat(result).isTrue();
        verify(blackListRepository, times(1)).existsByAccessToken(testAccessToken);
    }

    @Test
    @DisplayName("블랙리스트에 없는 토큰 확인 - False")
    void isBlacklisted_TokenNotExists_ReturnFalse() {
        // Given: 토큰이 블랙리스트에 없음
        when(blackListRepository.existsByAccessToken(testAccessToken)).thenReturn(false);

        // When: 블랙리스트 확인
        boolean result = blackListService.isBlacklisted(testAccessToken);

        // Then: False 반환
        assertThat(result).isFalse();
        verify(blackListRepository, times(1)).existsByAccessToken(testAccessToken);
    }

    @Test
    @DisplayName("만료된 블랙리스트 토큰 정리")
    void removeExpiredTokens_Success() {
        // When: 만료된 토큰 정리 실행
        blackListService.removeExpiredTokens();

        // Then: 삭제 메서드 호출 확인
        verify(blackListRepository, times(1)).deleteByExpirationBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("블랙리스트에 추가된 토큰의 만료 시간 검증")
    void addToBlacklist_ExpirationTime_Verification() {
        // Given: 만료 시간이 10분 후로 설정됨
        LocalDateTime exactExpiration = LocalDateTime.now().plusMinutes(10);

        // When: 블랙리스트에 토큰 추가
        blackListService.addToBlacklist(testEmail, testAccessToken, exactExpiration);

        // Then: 저장된 토큰의 만료 시간 확인
        ArgumentCaptor<BlackListToken> tokenCaptor = ArgumentCaptor.forClass(BlackListToken.class);
        verify(blackListRepository).save(tokenCaptor.capture());
        BlackListToken savedToken = tokenCaptor.getValue();

        assertThat(savedToken.getExpiration()).isEqualTo(exactExpiration);
        assertThat(savedToken.getExpiration()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("동일한 사용자가 여러 토큰을 블랙리스트에 추가 가능")
    void addToBlacklist_MultipleTokensForSameUser_Success() {
        // Given: 동일한 사용자의 여러 토큰
        String token1 = "token1";
        String token2 = "token2";

        // When: 여러 토큰을 블랙리스트에 추가
        blackListService.addToBlacklist(testEmail, token1, futureExpiration);
        blackListService.addToBlacklist(testEmail, token2, futureExpiration);

        // Then: 두 번 저장되었는지 확인
        verify(blackListRepository, times(2)).save(any(BlackListToken.class));
    }
}
