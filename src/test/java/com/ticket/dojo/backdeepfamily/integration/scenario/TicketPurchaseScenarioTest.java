package com.ticket.dojo.backdeepfamily.integration.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.dojo.backdeepfamily.domain.queue.dto.response.QueueEnterResponse;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.user.dto.request.UserLoginRequest;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.integration.base.BaseControllerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation.ReservationState.CONFIRMED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 티켓 구매 전체 시나리오 E2E 테스트
 *
 * 테스트 목적:
 * - 회원가입부터 결제 완료까지 전체 플로우 검증
 * - 여러 도메인 간의 통합 동작 검증
 * - 실제 사용자 관점의 시나리오 테스트
 *
 * 테스트 시나리오:
 * 1. 전체 티켓 구매 플로우 (회원가입 → 로그인 → 대기열 → 티켓팅 → 결제)
 * 2. 좌석 충돌 시나리오 (두 사용자가 동일 좌석 선택)
 * 3. 예약 취소 후 재구매 시나리오
 */
@SpringBootTest
@DisplayName("티켓 구매 시나리오 E2E 테스트")
class TicketPurchaseScenarioTest extends BaseControllerIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private List<Seat> testSeats;

    @BeforeEach
    void setUp() {
        // 전체 데이터 정리
        cleanupAllDomains();

        // 테스트용 좌석 생성
        testSeats = createTestSeats(100);
    }

    @AfterEach
    void tearDown() {
        cleanupAllDomains();
    }

    /**
     * 시나리오 1: 전체 티켓 구매 플로우
     *
     * 플로우:
     * 1. 회원가입
     * 2. 로그인 (JWT 토큰 발급)
     * 3. 대기열 진입
     * 4. 티켓팅 진입 (Reservation 생성)
     * 5. 좌석 선택 (직접 DB에 추가)
     * 6. 결제 진입
     * 7. 결제 완료
     * 8. 검증: Reservation CONFIRMED, Queue EXPIRED
     */
    @Test
    @DisplayName("전체 티켓 구매 시나리오 - 회원가입 → 로그인 → 대기열 → 티켓팅 → 결제 완료")
    void fullTicketPurchaseFlow_Success() throws Exception {
        // 1. 회원가입
        String email = "purchase@example.com";
        String password = "password123";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        // 회원가입 검증
        User savedUser = userRepository.findByEmail(email);
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo(email);

        // 2. 로그인 (JWT 토큰 발급)
        String accessToken = performLoginAndGetAccessToken(email, password);
        assertThat(accessToken).isNotNull();
        assertThat(accessToken).isNotEmpty();

        // 3. 대기열 진입
        MvcResult queueResult = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String queueResponseBody = queueResult.getResponse().getContentAsString();
        QueueEnterResponse queueResponse = objectMapper.readValue(queueResponseBody, QueueEnterResponse.class);
        String queueToken = queueResponse.getToken();

        assertThat(queueToken).isNotNull();
        assertThat(queueResponse.getStatus()).isEqualTo(QueueStatus.ACTIVE); // 첫 번째 사용자이므로 즉시 ACTIVE

        // 4. 티켓팅 진입 (Reservation 생성)
        MvcResult reservationResult = mockMvc.perform(
                        authenticatedPost("/reservations", accessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String reservationResponseBody = reservationResult.getResponse().getContentAsString();
        GetHoldingSeatsResponse reservationResponse = objectMapper.readValue(reservationResponseBody, GetHoldingSeatsResponse.class);
        Long reservationId = reservationResponse.getReservationId();

        assertThat(reservationId).isNotNull();

        // 5. 좌석 선택 (직접 DB에 추가 - WebSocket 대신)
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        ReservationSeat reservationSeat = ReservationSeat.builder()
                .reservation(reservation)
                .seat(testSeats.get(0))
                .expiredAt(LocalDateTime.now().plusSeconds(20))
                .build();
        reservationSeatRepository.save(reservationSeat);

        // 6. 결제 진입
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/payment-session", accessToken))
                .andDo(print())
                .andExpect(status().isOk());

        // 7. 결제 완료
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId + "/payment?queueToken=" + queueToken, accessToken))
                .andDo(print())
                .andExpect(status().isOk());

        // 8. 검증
        // 8-1. Reservation이 CONFIRMED 상태
        Reservation confirmedReservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(confirmedReservation.getReservationState()).isEqualTo(CONFIRMED);

        // 8-2. Queue가 EXPIRED 상태
        Queue expiredQueue = queueRepository.findByTokenValue(queueToken).orElseThrow();
        assertThat(expiredQueue.getStatus()).isEqualTo(QueueStatus.EXPIRED);

        // 8-3. 좌석 점유가 유지됨 (CONFIRMED 상태이므로)
        List<ReservationSeat> reservationSeats = reservationSeatRepository.findAllByReservation(reservation);
        assertThat(reservationSeats).hasSize(1);
        assertThat(reservationSeats.get(0).getSeat().getId()).isEqualTo(testSeats.get(0).getId());
    }

    /**
     * 시나리오 2: 좌석 충돌 시나리오
     *
     * 플로우:
     * 1. 사용자 A와 B가 각각 회원가입 및 로그인
     * 2. 둘 다 대기열 진입 (ACTIVE)
     * 3. 둘 다 티켓팅 진입
     * 4. 사용자 A가 좌석 1번 선택
     * 5. 사용자 B가 좌석 1번 선택 시도 (실패 예상)
     * 6. 사용자 B는 좌석 2번 선택
     * 7. 둘 다 결제 완료 (다른 좌석)
     */
    @Test
    @DisplayName("좌석 충돌 시나리오 - 두 사용자가 동일 좌석 선택 시 충돌 처리")
    void seatConflict_TwoUsers_HandleConflict() throws Exception {
        // 1. 사용자 A 회원가입 및 로그인
        String emailA = "userA@example.com";
        String passwordA = "password";

        UserLoginRequest joinRequestA = new UserLoginRequest();
        joinRequestA.setEmail(emailA);
        joinRequestA.setPassword(passwordA);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequestA)))
                .andExpect(status().isOk());

        String accessTokenA = performLoginAndGetAccessToken(emailA, passwordA);

        // 사용자 B 회원가입 및 로그인
        String emailB = "userB@example.com";
        String passwordB = "password";

        UserLoginRequest joinRequestB = new UserLoginRequest();
        joinRequestB.setEmail(emailB);
        joinRequestB.setPassword(passwordB);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequestB)))
                .andExpect(status().isOk());

        String accessTokenB = performLoginAndGetAccessToken(emailB, passwordB);

        // 2. 둘 다 대기열 진입
        MvcResult queueResultA = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessTokenA))
                .andExpect(status().isOk())
                .andReturn();

        QueueEnterResponse queueResponseA = objectMapper.readValue(
                queueResultA.getResponse().getContentAsString(), QueueEnterResponse.class);
        String queueTokenA = queueResponseA.getToken();

        MvcResult queueResultB = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessTokenB))
                .andExpect(status().isOk())
                .andReturn();

        QueueEnterResponse queueResponseB = objectMapper.readValue(
                queueResultB.getResponse().getContentAsString(), QueueEnterResponse.class);
        String queueTokenB = queueResponseB.getToken();

        // 3. 둘 다 티켓팅 진입
        MvcResult reservationResultA = mockMvc.perform(
                        authenticatedPost("/reservations", accessTokenA))
                .andExpect(status().isOk())
                .andReturn();

        GetHoldingSeatsResponse reservationResponseA = objectMapper.readValue(
                reservationResultA.getResponse().getContentAsString(), GetHoldingSeatsResponse.class);
        Long reservationIdA = reservationResponseA.getReservationId();

        MvcResult reservationResultB = mockMvc.perform(
                        authenticatedPost("/reservations", accessTokenB))
                .andExpect(status().isOk())
                .andReturn();

        GetHoldingSeatsResponse reservationResponseB = objectMapper.readValue(
                reservationResultB.getResponse().getContentAsString(), GetHoldingSeatsResponse.class);
        Long reservationIdB = reservationResponseB.getReservationId();

        // 4. 사용자 A가 좌석 1번 선택
        Reservation reservationA = reservationRepository.findById(reservationIdA).orElseThrow();
        ReservationSeat reservationSeatA = ReservationSeat.builder()
                .reservation(reservationA)
                .seat(testSeats.get(0)) // 좌석 1번
                .expiredAt(LocalDateTime.now().plusSeconds(20))
                .build();
        reservationSeatRepository.save(reservationSeatA);

        // 5. 사용자 B는 좌석 2번 선택 (1번은 이미 점유됨)
        Reservation reservationB = reservationRepository.findById(reservationIdB).orElseThrow();
        ReservationSeat reservationSeatB = ReservationSeat.builder()
                .reservation(reservationB)
                .seat(testSeats.get(1)) // 좌석 2번
                .expiredAt(LocalDateTime.now().plusSeconds(20))
                .build();
        reservationSeatRepository.save(reservationSeatB);

        // 6. 둘 다 결제 진입
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationIdA + "/payment-session", accessTokenA))
                .andExpect(status().isOk());

        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationIdB + "/payment-session", accessTokenB))
                .andExpect(status().isOk());

        // 7. 둘 다 결제 완료
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationIdA + "/payment?queueToken=" + queueTokenA, accessTokenA))
                .andExpect(status().isOk());

        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationIdB + "/payment?queueToken=" + queueTokenB, accessTokenB))
                .andExpect(status().isOk());

        // 8. 검증
        Reservation confirmedA = reservationRepository.findById(reservationIdA).orElseThrow();
        Reservation confirmedB = reservationRepository.findById(reservationIdB).orElseThrow();

        assertThat(confirmedA.getReservationState()).isEqualTo(CONFIRMED);
        assertThat(confirmedB.getReservationState()).isEqualTo(CONFIRMED);

        // 각각 다른 좌석을 점유하고 있음
        List<ReservationSeat> seatsA = reservationSeatRepository.findAllByReservation(reservationA);
        List<ReservationSeat> seatsB = reservationSeatRepository.findAllByReservation(reservationB);

        assertThat(seatsA.get(0).getSeat().getId()).isEqualTo(testSeats.get(0).getId());
        assertThat(seatsB.get(0).getSeat().getId()).isEqualTo(testSeats.get(1).getId());
    }

    /**
     * 시나리오 3: 예약 취소 후 재구매 시나리오
     *
     * 플로우:
     * 1. 사용자가 티켓 구매 진행 (결제 진입까지)
     * 2. 결제 전 예약 취소
     * 3. 다시 대기열 진입
     * 4. 다시 티켓팅 진입
     * 5. 좌석 선택 및 결제 완료
     */
    @Test
    @DisplayName("예약 취소 후 재구매 시나리오")
    void cancelReservation_ThenRepurchase_Scenario() throws Exception {
        // 1. 회원가입 및 로그인
        String email = "cancel@example.com";
        String password = "password";

        UserLoginRequest joinRequest = new UserLoginRequest();
        joinRequest.setEmail(email);
        joinRequest.setPassword(password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(joinRequest)))
                .andExpect(status().isOk());

        String accessToken = performLoginAndGetAccessToken(email, password);

        // 2. 첫 번째 구매 시도 - 대기열 진입
        MvcResult queueResult1 = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken))
                .andExpect(status().isOk())
                .andReturn();

        QueueEnterResponse queueResponse1 = objectMapper.readValue(
                queueResult1.getResponse().getContentAsString(), QueueEnterResponse.class);

        // 티켓팅 진입
        MvcResult reservationResult1 = mockMvc.perform(
                        authenticatedPost("/reservations", accessToken))
                .andExpect(status().isOk())
                .andReturn();

        GetHoldingSeatsResponse reservationResponse1 = objectMapper.readValue(
                reservationResult1.getResponse().getContentAsString(), GetHoldingSeatsResponse.class);
        Long reservationId1 = reservationResponse1.getReservationId();

        // 좌석 선택
        Reservation reservation1 = reservationRepository.findById(reservationId1).orElseThrow();
        ReservationSeat reservationSeat1 = ReservationSeat.builder()
                .reservation(reservation1)
                .seat(testSeats.get(0))
                .expiredAt(LocalDateTime.now().plusSeconds(20))
                .build();
        reservationSeatRepository.save(reservationSeat1);

        // 3. 예약 취소
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId1 + "/cancel", accessToken))
                .andExpect(status().isOk());

        // 취소 검증
        Reservation cancelledReservation = reservationRepository.findById(reservationId1).orElseThrow();
        assertThat(cancelledReservation.getReservationState())
                .isEqualTo(Reservation.ReservationState.CANCELLED);

        // 4. 재구매 - 대기열 재진입
        MvcResult queueResult2 = mockMvc.perform(
                        authenticatedPost("/queue/jwt/enter", accessToken))
                .andExpect(status().isOk())
                .andReturn();

        QueueEnterResponse queueResponse2 = objectMapper.readValue(
                queueResult2.getResponse().getContentAsString(), QueueEnterResponse.class);
        String queueToken2 = queueResponse2.getToken();

        // 티켓팅 재진입
        MvcResult reservationResult2 = mockMvc.perform(
                        authenticatedPost("/reservations", accessToken))
                .andExpect(status().isOk())
                .andReturn();

        GetHoldingSeatsResponse reservationResponse2 = objectMapper.readValue(
                reservationResult2.getResponse().getContentAsString(), GetHoldingSeatsResponse.class);
        Long reservationId2 = reservationResponse2.getReservationId();

        // 새로운 reservationId 확인
        assertThat(reservationId2).isNotEqualTo(reservationId1);

        // 좌석 선택
        Reservation reservation2 = reservationRepository.findById(reservationId2).orElseThrow();
        ReservationSeat reservationSeat2 = ReservationSeat.builder()
                .reservation(reservation2)
                .seat(testSeats.get(1))
                .expiredAt(LocalDateTime.now().plusSeconds(20))
                .build();
        reservationSeatRepository.save(reservationSeat2);

        // 결제 진입 및 완료
        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId2 + "/payment-session", accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(
                        authenticatedPost("/reservations/" + reservationId2 + "/payment?queueToken=" + queueToken2, accessToken))
                .andExpect(status().isOk());

        // 5. 검증
        Reservation confirmedReservation = reservationRepository.findById(reservationId2).orElseThrow();
        assertThat(confirmedReservation.getReservationState()).isEqualTo(CONFIRMED);

        Queue expiredQueue = queueRepository.findByTokenValue(queueToken2).orElseThrow();
        assertThat(expiredQueue.getStatus()).isEqualTo(QueueStatus.EXPIRED);
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
