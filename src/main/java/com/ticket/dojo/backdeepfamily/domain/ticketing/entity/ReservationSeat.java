package com.ticket.dojo.backdeepfamily.domain.ticketing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_seat")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSeat {
    private static final int HOLD_SECONDS = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "seat_id", unique = true)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "reservation_id")
    private Reservation reservation;

    public void refreshExpiredAt(LocalDateTime localDateTime) {
        expiredAt = localDateTime;
    }

    public static ReservationSeat createReservationSeat(Seat seat, Reservation reservation) {
        return ReservationSeat.builder()
                .seat(seat)
                .reservation(reservation)
                .expiredAt(LocalDateTime.now().plusSeconds(HOLD_SECONDS))
                .build();
    }
}
