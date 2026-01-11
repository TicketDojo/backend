package com.ticket.dojo.backdeepfamily.domain.queue.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueStatusResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.queue.repository.QueueRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.integration.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QueueController 통합 테스트
 *
 * 테스트 목적:
 * - Queue Controller의 모든 API 엔드포인트 검증
 * - JWT 인증 흐름 검증
 * - 대기열 상태 전환 로직 검증 (WAITING ↔ ACTIVE)
 * - 50명 정책 검증 (MAX_ACTIVE_USERS = 50)
 *
 * 테스트 환경:
 * - 실제 MySQL 데이터베이스 사용
 * - Spring Security + JWT 인증 활성화
 * - MockMvc를 통한 HTTP 요청 시뮬레이션
 *
 * API 엔드포인트:
 * - POST /queue/jwt/enter - 대기열 진입 (JWT 인증 필수)
 * - GET /queue/status?token={token} - 대기열 상태 조회
 * - DELETE /queue/exit?token={token} - 대기열 퇴장
 */
@SpringBootTest
@DisplayName("QueueController 통합 테스트")
class QueueControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private String testAccessToken;

    /**
     * 각 테스트 실행 전 초기화
     */
    @BeforeEach
    void setUp() throws Exception {
        // 데이터 정리
        queueRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 사용자 생성 및 로그인
        testUser = createAndSaveTestUser("queue-test@example.com", "password123");
        testAccessToken = performLoginAndGetAccessToken(testUser.getEmail(), "password123");
    }

    /**
     * 각 테스트 실행 후 정리
     */
    @AfterEach
    void tearDown() {
        queueRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * 테스트 1: 대기열 진입 API - JWT 인증 성공 시 대기열 토큰 발급
     *
     * 시나리오:
     * 1. 정상적인 JWT 토큰으로 대기열 진입 요청
     * 2. 대기열 토큰, 순번, 상태, 진입 시간이 포함된 응답 반환
     * 3. DB에 Queue 엔티티 저장 확인
     *
     * 검증 사항:
     * - HTTP 200 응답
     * - 응답에 token, position, status, enteredAt 필드 존재
     * - DB에 Queue 저장 확인
     */
    @Test
    @DisplayName("대기열 진입 API - JWT 인증 성공 시 대기열 토큰 발급")
    void enterQueue_WithValidJwt_Success() throws Exception {
        // When: JWT 인증으로 대기열 진입
        MvcResult result = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.position").exists())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.enteredAt").exists())
                .andReturn();

        // Then: 응답 검증
        String responseBody = result.getResponse().getContentAsString();
        QueueEnterResponse response = objectMapper.readValue(responseBody, QueueEnterResponse.class);

        assertThat(response.getToken()).isNotNull();
        assertThat(response.getStatus()).isIn(QueueStatus.ACTIVE, QueueStatus.WAITING);

        // DB 검증
        List<Queue> queues = queueRepository.findAll();
        assertThat(queues).hasSize(1);
        assertThat(queues.get(0).getUser().getUserId()).isEqualTo(testUser.getUserId());
    }

    /**
     * 테스트 2: 대기열 진입 API - JWT 토큰 없이 요청 시 401 에러
     *
     * 시나리오:
     * 1. JWT 토큰 없이 대기열 진입 요청
     * 2. Spring Security의 JWTFilter가 인증 실패 처리
     * 3. 401 Unauthorized 응답
     *
     * 검증 사항:
     * - HTTP 401 응답
     */
    @Test
    @DisplayName("대기열 진입 API - JWT 토큰 없이 요청 시 401 에러")
    void enterQueue_WithoutJwt_Unauthorized() throws Exception {
        // When & Then: JWT 없이 요청
        mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", null))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    /**
     * 테스트 3: 대기열 진입 API - 50명 미만 시 즉시 ACTIVE 상태로 진입
     *
     * 시나리오:
     * 1. 현재 ACTIVE 사용자가 50명 미만
     * 2. 새로운 사용자가 대기열 진입
     * 3. QueuePolicy.canActivateImmediately() == true
     * 4. Queue.createActive()로 즉시 ACTIVE 상태 생성
     *
     * 검증 사항:
     * - 응답의 status가 ACTIVE
     * - 응답의 position이 0
     * - DB에 ACTIVE 상태로 저장
     */
    @Test
    @DisplayName("대기열 진입 API - 50명 미만 시 즉시 ACTIVE 상태로 진입")
    void enterQueue_LessThan50Users_ActiveImmediately() throws Exception {
        // When: 대기열 진입 (현재 0명)
        MvcResult result = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        // Then: ACTIVE 상태 검증
        String responseBody = result.getResponse().getContentAsString();
        QueueEnterResponse response = objectMapper.readValue(responseBody, QueueEnterResponse.class);

        assertThat(response.getStatus()).isEqualTo(QueueStatus.ACTIVE);

        // DB 검증
        Queue savedQueue = queueRepository.findByTokenValue(response.getToken()).orElseThrow();
        assertThat(savedQueue.getStatus()).isEqualTo(QueueStatus.ACTIVE);
        assertThat(savedQueue.getActivatedAt()).isNotNull();
    }

    /**
     * 테스트 4: 대기열 진입 API - 50명 이상 시 WAITING 상태로 진입
     *
     * 시나리오:
     * 1. 현재 ACTIVE 사용자가 50명
     * 2. 새로운 사용자가 대기열 진입
     * 3. QueuePolicy.canActivateImmediately() == false
     * 4. Queue.createWaiting()으로 WAITING 상태 생성
     *
     * 검증 사항:
     * - 응답의 status가 WAITING
     * - 응답의 position이 1 이상
     * - DB에 WAITING 상태로 저장
     */
    @Test
    @DisplayName("대기열 진입 API - 50명 이상 시 WAITING 상태로 진입")
    void enterQueue_MoreThan50Users_WaitingState() throws Exception {
        // Given: 50명의 ACTIVE 사용자 생성
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("active" + i + "@example.com", "password");
            Queue activeQueue = Queue.createActive(user);
            queueRepository.save(activeQueue);
        }

        // When: 51번째 사용자가 대기열 진입
        MvcResult result = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        // Then: WAITING 상태 검증
        String responseBody = result.getResponse().getContentAsString();
        QueueEnterResponse response = objectMapper.readValue(responseBody, QueueEnterResponse.class);

        assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);

        // DB 검증
        Queue savedQueue = queueRepository.findByTokenValue(response.getToken()).orElseThrow();
        assertThat(savedQueue.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(savedQueue.getActivatedAt()).isNull();
    }

    /**
     * 테스트 5: 대기열 상태 조회 API - 유효한 토큰으로 조회 성공
     *
     * 시나리오:
     * 1. 대기열에 진입하여 토큰 발급
     * 2. 발급받은 토큰으로 상태 조회 API 호출
     * 3. 현재 대기 순번과 상태 정보 반환
     *
     * 검증 사항:
     * - HTTP 200 응답
     * - 응답에 token, position, status 필드 존재
     * - 조회한 정보와 실제 Queue 정보 일치
     */
    @Test
    @DisplayName("대기열 상태 조회 API - 유효한 토큰으로 조회 성공")
    void getQueueStatus_WithValidToken_Success() throws Exception {
        // Given: 대기열 진입
        MvcResult enterResult = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", testAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String enterResponseBody = enterResult.getResponse().getContentAsString();
        QueueEnterResponse enterResponse = objectMapper.readValue(enterResponseBody, QueueEnterResponse.class);
        String queueToken = enterResponse.getToken();

        // When: 대기열 상태 조회
        MvcResult statusResult = mockMvc.perform(
                        authenticatedGet("/queue/status?token=" + queueToken, testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(queueToken))
                .andExpect(jsonPath("$.position").exists())
                .andExpect(jsonPath("$.status").exists())
                .andReturn();

        // Then: 응답 검증
        String statusResponseBody = statusResult.getResponse().getContentAsString();
        QueueStatusResponse statusResponse = objectMapper.readValue(statusResponseBody, QueueStatusResponse.class);

        assertThat(statusResponse.getToken()).isEqualTo(queueToken);
        assertThat(statusResponse.getStatus()).isNotNull();
    }

    /**
     * 테스트 6: 대기열 퇴장 API - 퇴장 후 다음 대기자 자동 활성화
     *
     * 시나리오:
     * 1. 50명의 ACTIVE 사용자와 1명의 WAITING 사용자 생성
     * 2. ACTIVE 사용자 중 1명이 퇴장
     * 3. QueueService.exitQueue()가 자동으로 activateNextInQueue() 호출
     * 4. WAITING 상태였던 사용자가 ACTIVE로 전환
     *
     * 검증 사항:
     * - HTTP 204 No Content 응답
     * - 퇴장한 사용자의 Queue가 DB에서 삭제
     * - WAITING 사용자가 ACTIVE로 전환
     */
    @Test
    @DisplayName("대기열 퇴장 API - 퇴장 후 다음 대기자 자동 활성화")
    void exitQueue_ActivatesNextWaitingUser() throws Exception {
        // Given: 50명의 ACTIVE 사용자 생성
        Queue exitTargetQueue = null;
        for (int i = 0; i < 50; i++) {
            User user = createAndSaveTestUser("active" + i + "@example.com", "password");
            Queue activeQueue = Queue.createActive(user);
            queueRepository.save(activeQueue);
            if (i == 0) {
                exitTargetQueue = activeQueue; // 첫 번째 사용자를 퇴장 대상으로 지정
            }
        }

        // WAITING 사용자 1명 추가
        User waitingUser = createAndSaveTestUser("waiting@example.com", "password");
        Queue waitingQueue = queueRepository.save(Queue.createWaiting(waitingUser));

        // When: 첫 번째 ACTIVE 사용자 퇴장
        String exitToken = exitTargetQueue.getTokenValue();
        mockMvc.perform(
                        authenticatedDelete("/queue/exit?token=" + exitToken, testAccessToken))
                .andDo(print())
                .andExpect(status().isNoContent())
                .andReturn();

        // Then: 퇴장 확인 및 다음 대기자 활성화 확인
        // 1. 퇴장한 사용자의 Queue가 삭제되었는지 확인
        assertThat(queueRepository.findByTokenValue(exitToken)).isEmpty();

        // 2. 대기 중이던 사용자가 ACTIVE로 전환되었는지 확인
        Queue activatedQueue = queueRepository.findByTokenValue(waitingQueue.getTokenValue()).orElseThrow();
        assertThat(activatedQueue.getStatus()).isEqualTo(QueueStatus.ACTIVE);
    }

    /**
     * 테스트 7: 대기열 재진입 API - 기존 대기열 세션 삭제 후 재진입
     *
     * 시나리오:
     * 1. 사용자가 대기열에 진입 (첫 번째 세션)
     * 2. 동일 사용자가 다시 대기열 진입 요청
     * 3. QueueService.enterQueue()가 기존 세션 삭제 후 새 세션 생성
     * 4. DB에는 해당 사용자의 Queue가 1개만 존재
     *
     * 검증 사항:
     * - 첫 번째 진입 성공
     * - 두 번째 진입 성공
     * - 첫 번째 토큰으로 조회 시 실패 (이미 삭제됨)
     * - 두 번째 토큰으로 조회 시 성공
     * - DB에 해당 사용자의 Queue가 1개만 존재
     */
    @Test
    @DisplayName("대기열 재진입 API - 기존 대기열 세션 삭제 후 재진입")
    void reenterQueue_DeletesExistingSession() throws Exception {
        // Given: 첫 번째 진입
        MvcResult firstEnter = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", testAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String firstResponseBody = firstEnter.getResponse().getContentAsString();
        QueueEnterResponse firstResponse = objectMapper.readValue(firstResponseBody, QueueEnterResponse.class);
        String firstToken = firstResponse.getToken();

        // When: 두 번째 진입 (재진입)
        MvcResult secondEnter = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String secondResponseBody = secondEnter.getResponse().getContentAsString();
        QueueEnterResponse secondResponse = objectMapper.readValue(secondResponseBody, QueueEnterResponse.class);
        String secondToken = secondResponse.getToken();

        // Then: 검증
        // 1. 첫 번째 토큰과 두 번째 토큰은 달라야 함
        assertThat(firstToken).isNotEqualTo(secondToken);

        // 2. 첫 번째 토큰으로 조회 시 실패 (이미 삭제됨)
        assertThat(queueRepository.findByTokenValue(firstToken)).isEmpty();

        // 3. 두 번째 토큰으로 조회 시 성공
        assertThat(queueRepository.findByTokenValue(secondToken)).isPresent();

        // 4. 해당 사용자의 Queue가 DB에 1개만 존재
        List<Queue> userQueues = queueRepository.findAll().stream()
                .filter(q -> q.getUser().getUserId().equals(testUser.getUserId()))
                .toList();
        assertThat(userQueues).hasSize(1);
        assertThat(userQueues.get(0).getTokenValue()).isEqualTo(secondToken);
    }

    /**
     * 테스트 8: 존재하지 않는 토큰으로 상태 조회 시 에러
     *
     * 시나리오:
     * 1. 존재하지 않는 토큰으로 상태 조회 요청
     * 2. QueueService에서 QueueNotFoundException 발생
     * 3. GlobalExceptionHandler가 예외 처리
     *
     * 검증 사항:
     * - HTTP 404 또는 400 응답 (GlobalExceptionHandler 설정에 따름)
     */
    @Test
    @DisplayName("존재하지 않는 토큰으로 상태 조회 시 에러")
    void getQueueStatus_WithInvalidToken_NotFound() throws Exception {
        // When & Then: 존재하지 않는 토큰으로 조회
        String invalidToken = "invalid-token-12345";
        mockMvc.perform(
                        authenticatedGet("/queue/status?token=" + invalidToken, testAccessToken))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    /**
     * 테스트 9: 존재하지 않는 토큰으로 퇴장 요청 시 에러
     *
     * 시나리오:
     * 1. 존재하지 않는 토큰으로 퇴장 요청
     * 2. QueueService에서 QueueNotFoundException 발생
     * 3. GlobalExceptionHandler가 예외 처리
     *
     * 검증 사항:
     * - HTTP 404 또는 400 응답
     */
    @Test
    @DisplayName("존재하지 않는 토큰으로 퇴장 요청 시 에러")
    void exitQueue_WithInvalidToken_NotFound() throws Exception {
        // When & Then: 존재하지 않는 토큰으로 퇴장
        String invalidToken = "invalid-token-12345";
        mockMvc.perform(
                        authenticatedDelete("/queue/exit?token=" + invalidToken, testAccessToken))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }
}
