package com.ticket.dojo.backdeepfamily.domain.queue.entity;

import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

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

    @Embedded
    private Token token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status; // 큐 상태

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "position"))
    private Position position; // 대기 순번

    @Column(nullable = false)
    private LocalDateTime enteredAt; // 입장 시간

    // private LocalDateTime expiresAt; // 만료 시간

    private LocalDateTime activatedAt; // ACTIVE 상태로 변경된 시간

    @Column(nullable = false)
    private LocalDateTime updatedAt; // 마지막 업데이트 시간

    @Builder(access = AccessLevel.PRIVATE)
    private Queue(User user, Token token, QueueStatus status, Position position,
                  LocalDateTime enteredAt, LocalDateTime activatedAt, LocalDateTime updatedAt){
        this.user = user;
        this.token = token;
        this.status = status;
        this.position = position;
        this.enteredAt = enteredAt;
        this.activatedAt = activatedAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 즉시 활성화된 대기열 생성
     */
    public static Queue createActive(User user) {
        LocalDateTime now = LocalDateTime.now();
        return Queue.builder()
                .user(user)
                .token(Token.generate())
                .status(QueueStatus.ACTIVE)
                .position(Position.zero())
                .enteredAt(now)
                .activatedAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 대기 상태의 대기열 생성
     */
    public static Queue createWaiting(User user, Position position) {
        LocalDateTime now = LocalDateTime.now();
        return Queue.builder()
                .user(user)
                .token(Token.generate())
                .status(QueueStatus.WAITING)
                .position(position)
                .enteredAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 대기열 활성화
     */
    public void activate() {
        validateStateTransition(QueueStatus.ACTIVE);

        this.status = QueueStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 대기열 만료
     */
    public void expire() {
        validateStateTransition(QueueStatus.EXPIRED);

        this.status = QueueStatus.EXPIRED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 상태 전환 유효성 검증
     */
    private void validateStateTransition(QueueStatus targetStatus) {
        if (!this.status.canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    String.format("현재 상태(%s)에서 %s 상태로 전환할 수 없습니다",
                            this.status.getDescription(),
                            targetStatus.getDescription())
            );
        }
    }

    /**
     * 대기 중인지 확인
     */
    public boolean isWaiting() {
        return this.status.isWaiting();
    }

    /**
     * 활성화 상태인지 확인
     */
    public boolean isActive() {
        return this.status.isActive();
    }

    /**
     * 만료되었는지 확인
     */
    public boolean isExpired() {
        return this.status.isExpired();
    }

    /**
     * 토큰 값 반환 (편의 메서드)
     */
    public String getTokenValue() {
        return this.token.getValue();
    }

    /**
     * 순번 값 반환 (편의 메서드)
     */
    public int getPositionValue() {
        return this.position.getValue();
    }

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
}
