package com.ticket.dojo.backdeepfamily.domain.queue.repository;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueRepository extends JpaRepository<Queue, Long> {

    // Token 값으로 Queue 조회
    // Token이 @Embeddable이므로 명시적 쿼리 필요
    @Query("SELECT q FROM Queue q WHERE q.token.value = :tokenValue")
    Optional<Queue> findByTokenValue(@Param("tokenValue") String tokenValue);

    // 특정 상태의 Queue 개수 조회
    // SELECT count(*) FROM queue WHERE status = ?
    int countByStatus(QueueStatus status);

    // 특정 시간 이전에 들어온 특정 상태의 Queue 개수
    // SELECT count(*) FROM queue WHERE status = ? AND entered_at < ?
    int countByStatusAndEnteredAtBefore(QueueStatus queueStatus, LocalDateTime enteredAt);

    List<Queue> findByStatusOrderByEnteredAtAsc(QueueStatus status, Pageable pageable);

    Optional<Queue> findByUserAndStatusIn(User user, List<QueueStatus> active);

    List<Queue> user(User user);

    // 비관적 락을 이용한 상태별 Queue 개수 조회 (동시성 제어용)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COUNT(q) FROM Queue q WHERE q.status = :status")
    int countByStatusWithLock(@Param("status") QueueStatus status);
}
