package com.ticket.dojo.backdeepfamily.domain.reservation.entity;

import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime reservationStart;

    @Column(nullable = false)
    private LocalDateTime reservationEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED') DEFAULT 'PENDING'")
    private ReservationState reservationState;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    private Long sequenceNum;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.reservationState == null) {
            this.reservationState = ReservationState.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum ReservationState {
        PENDING, CONFIRMED, CANCELLED, EXPIRED
    }
}
