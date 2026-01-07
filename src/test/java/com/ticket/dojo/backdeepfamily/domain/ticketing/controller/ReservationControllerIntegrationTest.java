package com.ticket.dojo.backdeepfamily.domain.ticketing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.service.QueueService;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetRankingResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.integration.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation.ReservationState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReservationController 통합 테스트
 *
 * 테스트 목적:
 * - Reservation Controller의 모든 API 엔드포인트 검증
 * - JWT 인증 흐름 검증
 * - HTTP 요청/응답 검증
 * - 권한 검증
 *
 * API 엔드포인트:
 * - POST /reservations - 티켓팅 진입 (JWT 인증 필수)
 * - POST /reservations/{reservationId}/payment-session - 결제 진입
 * - POST /reservations/{reservationId}/payment - 결제 완료 (JWT 인증 필수)
 * - GET /reservations/{reservationId}/rank - 랭킹 조회
 * - POST /reservations/{reservationId}/cancel - 예약 취소 (JWT 인증 필수)
 */
@SpringBootTest
@DisplayName("ReservationController 통합 테스트")
class ReservationControllerIntegrationTest extends BaseControllerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueueService queueService;

    private User testUser;
    private String testAccessToken;
    private List<Seat> testSeats;

    @BeforeEach
    void setUp() throws Exception {
        // 데이터 정리
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        queueRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();

        // 테스트용 사용자 생성 및 로그인
        testUser = createAndSaveTestUser("reservation-ctrl@example.com", "password123");
        testAccessToken = performLoginAndGetAccessToken(testUser.getEmail(), "password123");

        // 테스트용 좌석 생성
        testSeats = createTestSeats(10);
    }

    @AfterEach
    void tearDown() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        queueRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * 테스트 1: 티켓팅 진입 API - JWT 인증 필수
     */
    @Test
    @DisplayName("티켓팅 진입 API - POST /reservations - JWT 인증 성공")
    void enterTicketing_WithValidJwt_ReturnsReservationAndHoldingSeats() throws Exception {
        // when
        MvcResult result = mockMvc.perform(
                        authenticatedPost("/reservations", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").exists())
                .andExpect(jsonPath("$.sequenceNum").exists())
                .andExpect(jsonPath("$.seats").isArray())
                .andReturn();

        // then
        String responseBody = result.getResponse().getContentAsString();
        GetHoldingSeatsResponse response = objectMapper.readValue(responseBody, GetHoldingSeatsResponse.class);

        assertThat(response.getReservationId()).isNotNull();
        assertThat(response.getSequenceNum()).isGreaterThanOrEqualTo(0);

        // DB 검증
        Reservation reservation = reservationRepository.findById(response.getReservationId()).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(PENDING);
        assertThat(reservation.getUser().getUserId()).isEqualTo(testUser.getUserId());
    }

    /**
     * 테스트 2: 티켓팅 진입 API - JWT 없이 요청 시 401 에러
     */
    @Test
    @DisplayName("티켓팅 진입 API - JWT 없이 요청 시 401 에러")
    void enterTicketing_WithoutJwt_Unauthorized() throws Exception {
        // when & then
        mockMvc.perform(
                        authenticatedPost("/reservations", null))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    /**
     * 테스트 3: 결제 진입 API - POST /reservations/{reservationId}/payment-session
     */
    @Test
    @DisplayName("결제 진입 API - 유효한 reservationId로 요청 성공")
    void enterPaying_WithValidReservationId_Returns200() throws Exception {
        // given
        MvcResult ticketingResult = mockMvc.perform(
                        authenticatedPost("/reservations", testAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = ticketingResult.getResponse().getContentAsString();
        GetHoldingSeatsResponse ticketingResponse = objectMapper.readValue(responseBody, GetHoldingSeatsResponse.class);
        Long reservationId = ticketingResponse.getReservationId();

        // when
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/payment-session", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        // then
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(PAYING);
    }

    /**
     * 테스트 4: 결제 완료 API - POST /reservations/{reservationId}/payment
     */
    @Test
    @DisplayName("결제 완료 API - 유효한 데이터로 요청 성공 및 대기열 만료")
    void completePaying_WithValidData_Returns200_AndExpiresQueue() throws Exception {
        // given
        // 대기열 진입
        QueueEnterResponse queueResponse = queueService.enterQueue(testUser.getUserId());
        String queueToken = queueResponse.getToken();

        // 티켓팅 진입
        MvcResult ticketingResult = mockMvc.perform(
                        authenticatedPost("/reservations", testAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = ticketingResult.getResponse().getContentAsString();
        GetHoldingSeatsResponse ticketingResponse = objectMapper.readValue(responseBody, GetHoldingSeatsResponse.class);
        Long reservationId = ticketingResponse.getReservationId();

        // 결제 진입
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/payment-session", testAccessToken))
                .andExpect(status().isOk());

        // when
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/payment?queueToken=" + queueToken, testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        // then
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getReservationState()).isEqualTo(CONFIRMED);

        // 대기열 만료 확인
        var queue = queueRepository.findByTokenValue(queueToken).orElseThrow();
        assertThat(queue.isExpired()).isTrue();
    }

    /**
     * 테스트 5: 랭킹 조회 API - GET /reservations/{reservationId}/rank
     */
    @Test
    @DisplayName("랭킹 조회 API - 랭킹 리스트 반환")
    void getRanking_ReturnsRankingList() throws Exception {
        // given
        // 여러 사용자의 예약 생성 및 확정
        User user1 = createAndSaveTestUser("rank1@example.com", "password");
        User user2 = createAndSaveTestUser("rank2@example.com", "password");

        // Reservation 생성 및 CONFIRMED 상태로 변경
        Reservation res1 = Reservation.createReservation(user1, 1L);
        res1.changeState(CONFIRMED);
        reservationRepository.save(res1);

        Reservation res2 = Reservation.createReservation(user2, 1L);
        res2.changeState(CONFIRMED);
        reservationRepository.save(res2);

        // when
        MvcResult result = mockMvc.perform(
                        authenticatedGet("/reservations/" + res1.getId() + "/rank", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranks").isArray())
                .andReturn();

        // then
        String responseBody = result.getResponse().getContentAsString();
        GetRankingResponse response = objectMapper.readValue(responseBody, GetRankingResponse.class);

        assertThat(response.getRanks()).hasSizeGreaterThanOrEqualTo(2);
    }

    /**
     * 테스트 6: 예약 취소 API - POST /reservations/{reservationId}/cancel
     */
    @Test
    @DisplayName("예약 취소 API - 유효한 데이터로 요청 성공")
    void cancelReservation_WithValidData_Returns200() throws Exception {
        // given
        MvcResult ticketingResult = mockMvc.perform(
                        authenticatedPost("/reservations", testAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = ticketingResult.getResponse().getContentAsString();
        GetHoldingSeatsResponse ticketingResponse = objectMapper.readValue(responseBody, GetHoldingSeatsResponse.class);
        Long reservationId = ticketingResponse.getReservationId();

        // 좌석 점유 추가
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        ReservationSeat reservationSeat = ReservationSeat.builder()
                .reservation(reservation)
                .seat(testSeats.get(0))
                .expiredAt(LocalDateTime.now().plusSeconds(20))
                .build();
        reservationSeatRepository.save(reservationSeat);

        // when
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/cancel", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        // then
        Reservation cancelledReservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(cancelledReservation.getReservationState()).isEqualTo(CANCELLED);

        // 좌석 점유 해제 확인
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findAllByReservation(reservation);
        assertThat(reservationSeats).isEmpty();
    }

    /**
     * 테스트 7: 예약 취소 API - 다른 사용자의 예약 취소 시도 시 403 에러
     */
    @Test
    @DisplayName("예약 취소 API - 다른 사용자의 예약 취소 시도 시 에러")
    void cancelReservation_ByDifferentUser_Forbidden() throws Exception {
        // given
        MvcResult ticketingResult = mockMvc.perform(
                        authenticatedPost("/reservations", testAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = ticketingResult.getResponse().getContentAsString();
        GetHoldingSeatsResponse ticketingResponse = objectMapper.readValue(responseBody, GetHoldingSeatsResponse.class);
        Long reservationId = ticketingResponse.getReservationId();

        // 다른 사용자 생성 및 로그인
        User otherUser = createAndSaveTestUser("other@example.com", "password");
        String otherAccessToken = performLoginAndGetAccessToken(otherUser.getEmail(), "password");

        // when & then
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/cancel", otherAccessToken))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    /**
     * 테스트 8: 존재하지 않는 reservationId로 요청 시 에러
     */
    @Test
    @DisplayName("결제 진입 API - 존재하지 않는 reservationId로 요청 시 에러")
    void enterPaying_WithInvalidReservationId_NotFound() throws Exception {
        // when & then
        mockMvc.perform(
                        authenticatedPost("/reservations/99999/payment-session", testAccessToken))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    /**
     * 테스트 9: 전체 예약 플로우 - 티켓팅 → 결제 진입 → 결제 완료
     */
    @Test
    @DisplayName("전체 예약 플로우 - 티켓팅 진입 → 결제 진입 → 결제 완료")
    void fullReservationFlow_Success() throws Exception {
        // given
        // 대기열 진입
        QueueEnterResponse queueResponse = queueService.enterQueue(testUser.getUserId());
        String queueToken = queueResponse.getToken();

        // 1. 티켓팅 진입
        MvcResult ticketingResult = mockMvc.perform(
                        authenticatedPost("/reservations", testAccessToken))
                .andExpect(status().isOk())
                .andReturn();

        String ticketingBody = ticketingResult.getResponse().getContentAsString();
        GetHoldingSeatsResponse ticketingResponse = objectMapper.readValue(ticketingBody, GetHoldingSeatsResponse.class);
        Long reservationId = ticketingResponse.getReservationId();

        // 검증: PENDING 상태
        Reservation afterTicketing = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(afterTicketing.getReservationState()).isEqualTo(PENDING);

        // 2. 결제 진입
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/payment-session", testAccessToken))
                .andExpect(status().isOk());

        // 검증: PAYING 상태
        Reservation afterPayingEntry = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(afterPayingEntry.getReservationState()).isEqualTo(PAYING);

        // 3. 결제 완료
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/payment?queueToken=" + queueToken, testAccessToken))
                .andExpect(status().isOk());

        // 검증: CONFIRMED 상태 및 대기열 만료
        Reservation afterPayingComplete = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(afterPayingComplete.getReservationState()).isEqualTo(CONFIRMED);

        var queue = queueRepository.findByTokenValue(queueToken).orElseThrow();
        assertThat(queue.isExpired()).isTrue();
    }

    /**
     * 테스트용 좌석 생성 헬퍼 메서드
     */
    private List<Seat> createTestSeats(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> {
                    Seat seat = Seat.builder()
                            .seatNumber("A" + (i + 1))
                            .build();
                    return seatRepository.save(seat);
                })
                .toList();
    }
}
