package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.ReservationSeat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Seat;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.SeatRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.global.exception.socket.ReservationNotFoundException;
import com.ticket.dojo.backdeepfamily.global.exception.socket.SeatAlreadyHeldException;
import com.ticket.dojo.backdeepfamily.global.exception.socket.SeatNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketingSocketService 단위 테스트")
class TicketingSocketServiceImplTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationSeatRepository reservationSeatRepository;

    @InjectMocks
    private TicketingSocketServiceImpl ticketingSocketService;

    private User testUser;
    private Reservation testReservation;
    private Seat testSeat;
    private Long testSeatId;
    private Long testReservationId;
    private Long testSequenceNum;

    @BeforeEach
    void setUp() {
        testSeatId = 1L;
        testReservationId = 1L;
        testSequenceNum = 1L;

        testUser = User.builder()
                .userId(1L)
                .name("테스트유저")
                .email("test@example.com")
                .build();

        testReservation = Reservation.builder()
                .id(testReservationId)
                .user(testUser)
                .sequenceNum(testSequenceNum)
                .reservationState(Reservation.ReservationState.PENDING)
                .build();

        testSeat = Seat.builder()
                .id(testSeatId)
                .seatNumber("A1")
                .build();
    }

    @Test
    @DisplayName("좌석 점유 성공")
    void holdSeat_Success() {
        // given
        given(reservationRepository.findById(testReservationId))
                .willReturn(Optional.of(testReservation));
        given(seatRepository.findById(testSeatId))
                .willReturn(Optional.of(testSeat));
        given(reservationSeatRepository.existsBySeatAndSequenceNum(testSeat, testSequenceNum))
                .willReturn(false);
        given(reservationSeatRepository.save(any(ReservationSeat.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ticketingSocketService.holdSeat(testSeatId, testReservationId, testSequenceNum);

        // then
        verify(reservationRepository, times(1)).findById(testReservationId);
        verify(seatRepository, times(1)).findById(testSeatId);
        verify(reservationSeatRepository, times(1)).existsBySeatAndSequenceNum(testSeat, testSequenceNum);
        verify(reservationSeatRepository, times(1)).save(any(ReservationSeat.class));
    }

    @Test
    @DisplayName("좌석 점유 실패 - 예약을 찾을 수 없음")
    void holdSeat_ReservationNotFound() {
        // given
        given(reservationRepository.findById(testReservationId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ticketingSocketService.holdSeat(testSeatId, testReservationId, testSequenceNum))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("예약을 찾을 수 없습니다");

        verify(reservationRepository, times(1)).findById(testReservationId);
        verify(seatRepository, never()).findById(any());
        verify(reservationSeatRepository, never()).save(any());
    }

    @Test
    @DisplayName("좌석 점유 실패 - 좌석을 찾을 수 없음")
    void holdSeat_SeatNotFound() {
        // given
        given(reservationRepository.findById(testReservationId))
                .willReturn(Optional.of(testReservation));
        given(seatRepository.findById(testSeatId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ticketingSocketService.holdSeat(testSeatId, testReservationId, testSequenceNum))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining("좌석을 찾을 수 없습니다");

        verify(reservationRepository, times(1)).findById(testReservationId);
        verify(seatRepository, times(1)).findById(testSeatId);
        verify(reservationSeatRepository, never()).save(any());
    }

    @Test
    @DisplayName("좌석 점유 실패 - 이미 점유된 좌석")
    void holdSeat_SeatAlreadyHeld() {
        // given
        given(reservationRepository.findById(testReservationId))
                .willReturn(Optional.of(testReservation));
        given(seatRepository.findById(testSeatId))
                .willReturn(Optional.of(testSeat));
        given(reservationSeatRepository.existsBySeatAndSequenceNum(testSeat, testSequenceNum))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> ticketingSocketService.holdSeat(testSeatId, testReservationId, testSequenceNum))
                .isInstanceOf(SeatAlreadyHeldException.class)
                .hasMessageContaining("이미 점유된 좌석입니다");

        verify(reservationRepository, times(1)).findById(testReservationId);
        verify(seatRepository, times(1)).findById(testSeatId);
        verify(reservationSeatRepository, times(1)).existsBySeatAndSequenceNum(testSeat, testSequenceNum);
        verify(reservationSeatRepository, never()).save(any());
    }

    @Test
    @DisplayName("좌석 해제 성공")
    void releaseSeat_Success() {
        // given
        ReservationSeat reservationSeat = ReservationSeat.builder()
                .id(1L)
                .seat(testSeat)
                .reservation(testReservation)
                .sequenceNum(testSequenceNum)
                .build();

        given(reservationRepository.findById(testReservationId))
                .willReturn(Optional.of(testReservation));
        given(seatRepository.findById(testSeatId))
                .willReturn(Optional.of(testSeat));
        given(reservationSeatRepository.findAllByReservation(testReservation))
                .willReturn(java.util.List.of(reservationSeat));

        // when
        ticketingSocketService.releaseSeat(testReservationId, testSequenceNum, testSeatId);

        // then
        verify(reservationRepository, times(1)).findById(testReservationId);
        verify(seatRepository, times(1)).findById(testSeatId);
        verify(reservationSeatRepository, times(1)).findAllByReservation(testReservation);
        verify(reservationSeatRepository, times(1)).delete(reservationSeat);
    }

    @Test
    @DisplayName("좌석 해제 실패 - 예약을 찾을 수 없음")
    void releaseSeat_ReservationNotFound() {
        // given
        given(reservationRepository.findById(testReservationId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ticketingSocketService.releaseSeat(testReservationId, testSequenceNum, testSeatId))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("예약을 찾을 수 없습니다");

        verify(reservationRepository, times(1)).findById(testReservationId);
        verify(seatRepository, never()).findById(any());
        verify(reservationSeatRepository, never()).delete(any());
    }

    @Test
    @DisplayName("좌석 해제 실패 - 좌석을 찾을 수 없음")
    void releaseSeat_SeatNotFound() {
        // given
        given(reservationRepository.findById(testReservationId))
                .willReturn(Optional.of(testReservation));
        given(seatRepository.findById(testSeatId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ticketingSocketService.releaseSeat(testReservationId, testSequenceNum, testSeatId))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining("좌석을 찾을 수 없습니다");

        verify(reservationRepository, times(1)).findById(testReservationId);
        verify(seatRepository, times(1)).findById(testSeatId);
        verify(reservationSeatRepository, never()).delete(any());
    }
}
