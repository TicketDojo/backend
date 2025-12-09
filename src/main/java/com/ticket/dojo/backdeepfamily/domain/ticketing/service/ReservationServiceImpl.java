package com.ticket.dojo.backdeepfamily.domain.ticketing.service;

import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetHoldingSeatsResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.dto.response.GetRankingResponse;
import com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationRepository;
import com.ticket.dojo.backdeepfamily.domain.ticketing.repository.ReservationSeatRepository;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import com.ticket.dojo.backdeepfamily.domain.user.repository.UserRepository;
import com.ticket.dojo.backdeepfamily.global.exception.ReservationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static com.ticket.dojo.backdeepfamily.domain.ticketing.entity.Reservation.ReservationState.*;

@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;
    private static final int HOLD_SECONDS = 20;

    //회차구할때 기준점
    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2025, 12, 9, 10, 0, 0);

    private long getCurrentSequenceNum() {
        return ChronoUnit.MINUTES.between(BASE_TIME, LocalDateTime.now()); //내림차순 30분 50초면 -> 30
    }

    /**
     * PAYING로 상태 바꾸기
     * 좌석 점유 시간 20초로 초기화
     */
    @Transactional
    @Override
    public void enterPaying(Long reservationId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException("예약을 찾을 수 없습니다. 예약 ID : " + reservationId));

        reservation.changeState(PAYING);

        reservationSeatRepository.findAllByReservation(reservation)
                .forEach(seat ->
                        seat.refreshExpiredAt(LocalDateTime.now().plusSeconds(HOLD_SECONDS)) //지금부터 + 20초로 초기화
                );
    }

    /**
     * Reservation 객체 생성해서 id 넘겨주고
     * 현재 점유중인 좌석 넘겨주기
     */
    @Transactional
    @Override
    public GetHoldingSeatsResponse enterTicketing(Long userId) {
        User findUser = userRepository.findById(userId).orElseThrow(); //todo: 예외 가져오기
        long sequenceNum = getCurrentSequenceNum();
        Reservation reservation = Reservation.builder()
                .reservationState(PENDING)
                .user(findUser)
                .sequenceNum(sequenceNum)
                .build();

        reservationRepository.save(reservation);

        List<GetHoldingSeatsResponse.HoldingSeatDto> holdingSeats = reservationSeatRepository.findAllBySequenceNum(sequenceNum)
                .stream()
                .map(rs -> new GetHoldingSeatsResponse.HoldingSeatDto(
                        rs.getSeat().getId(), rs.getSeat().getSeatNumber()))
                .collect(Collectors.toList());

        return GetHoldingSeatsResponse.builder()
                .seats(holdingSeats)
                .reservationId(reservation.getId())
                .sequenceNum(sequenceNum)
                .build();
    }

    @Transactional
    @Override
    public void completePaying(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException("예약을 찾을 수 없습니다. 예약 ID : " + reservationId));

        if (reservation.getUser().getUserId().equals(userId)) {
            throw new ReservationException("비정상적인 접근입니다.");
        }
        reservation.changeState(CONFIRMED);
    }


    @Transactional
    @Override
    public GetRankingResponse getRanking(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException("예약을 찾을 수 없습니다. 예약 ID : " + reservationId));

        long sequenceNum = reservation.getSequenceNum();

        List<GetRankingResponse.RankDto> ranking =
                reservationRepository.findAllBySequenceNumAndReservationStateOrderByUpdatedAtAsc(
                                sequenceNum, CONFIRMED
                        )
                        .stream()
                        .map(rs ->
                                new GetRankingResponse.RankDto(
                                        rs.getUser().getName(),
                                        String.valueOf(rs.getUpdatedAt())
                                )
                        )
                        .toList();

        return GetRankingResponse.builder()
                .ranks(ranking).build();
    }

    @Transactional
    @Override
    public void cancelReservation(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationException("예약을 찾을 수 없습니다."));

        if (!reservation.getUser().getUserId().equals(userId)) {
            throw new ReservationException("예약 취소 권한이 없습니다.");
        }

        if (reservation.getReservationState() == CONFIRMED) {
            throw new ReservationException("확정된 예약은 취소할 수 없습니다.");
        }

        // 좌석 점유 해제
        reservationSeatRepository.deleteAll(reservationSeatRepository.findAllByReservation(reservation));

        reservation.changeState(Reservation.ReservationState.CANCELLED);
    }
}
