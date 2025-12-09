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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(nullable = false)
    private Long sequenceNum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "seat_id")
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "reservation_id")
    private Reservation reservation;

    public void refreshExpiredAt(LocalDateTime localDateTime) {
        expiredAt = localDateTime;
    }
}
