package com.ticket.dojo.backdeepfamily.domain.seat.entity;

import com.ticket.dojo.backdeepfamily.domain.reservation.entity.Reservation;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seat")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long floor;

    @Column(nullable = false, length = 1)
    private String section;

    @Column(nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('AVAILABLE', 'PENDING', 'CONFIRMED') DEFAULT 'AVAILABLE'")
    private SeatState seatState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "reservation_id")
    private Reservation reservation;

    @PrePersist
    public void prePersist() {
        if (this.seatState == null) {
            this.seatState = SeatState.AVAILABLE;
        }
    }

    public enum SeatState {
        AVAILABLE, PENDING, CONFIRMED
    }
}
