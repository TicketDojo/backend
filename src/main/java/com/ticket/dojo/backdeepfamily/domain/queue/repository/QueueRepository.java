package com.ticket.dojo.backdeepfamily.domain.queue.repository;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.queue.entity.QueueStatus;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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

    // 비관적 락을 사용한 상태별 Queue 개수 조회
    // 해당 상태의 모든 Queue에 FOR UPDATE 락을 걸고 카운트 반환
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "javax.persistence.lock.timeout", value = "3000")})
    @Query("SELECT q FROM Queue q WHERE q.status = :status")
    List<Queue> findByStatusWithLock(@Param("status") QueueStatus status);

    // countByStatusWithLock 대체 메서드 (List 크기로 카운트)
    default int countByStatusWithLock(QueueStatus status) {
        return findByStatusWithLock(status).size();
    }

    // 특정 시간 이전에 들어온 특정 상태의 Queue 개수
    // SELECT count(*) FROM queue WHERE status = ? AND entered_at < ?
    int countByStatusAndEnteredAtBefore(QueueStatus queueStatus, LocalDateTime enteredAt);

    // WAITING 상태를 enteredAt 순으로 조회 (Pageable로 개수 제어)
    // SELECT * FROM queue WHERE status = ? ORDER BY entered_at ASC LIMIT ?
    List<Queue> findByStatusOrderByEnteredAtAsc(QueueStatus status, Pageable pageable);

    // 특정 사용자의 먼저 들어온 대기열 상태가 있는지 반환
    Optional<Queue> findByUserAndStatusIn(User user, List<QueueStatus> active);

    List<Queue> user(User user);
}
