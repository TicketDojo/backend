package com.ticket.dojo.backdeepfamily.domain.queue.entity;

import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "queue")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Queue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(unique = true, nullable = false, length = 100)
    private String token; // 대기열 검증용 고유 토큰

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status; // 큐 상태

    private int position; // 대기 순번

    @Column(nullable = false)
    private LocalDateTime enteredAt; // 입장 시간

    // private LocalDateTime expiresAt; // 만료 시간

    private LocalDateTime activatedAt; // ACTIVE 상태로 변경된 시간

    @Column(nullable = false)
    private LocalDateTime updatedAt; // 마지막 업데이트 시간

    @PrePersist
    public void prePersist() {
        if (this.enteredAt == null) {
            this.enteredAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }

        if (this.status == null) {
            this.status = QueueStatus.WAITING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum QueueStatus {
        WAITING, // 대기 중
        ACTIVE, // 활성화 (입장 가능)
        EXPIRED // 만료됨
    }

    public static Queue createWaitQueue(User user, String token, int position) {
        return Queue.builder()
                .user(user)
                .token(token)
                .status(QueueStatus.WAITING)
                .position(position)
                .enteredAt(LocalDateTime.now())
                .build();
    }

    public void activate(LocalDateTime now) {
        this.status = QueueStatus.ACTIVE;
        this.activatedAt = now;
        // this.expiresAt = now.plusMinutes(10); // 삭제됨
        this.updatedAt = now;
    }

    public void expire() {
        this.status = QueueStatus.EXPIRED;
    }
}
