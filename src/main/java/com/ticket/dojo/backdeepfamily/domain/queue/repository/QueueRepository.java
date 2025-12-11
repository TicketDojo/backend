package com.ticket.dojo.backdeepfamily.domain.queue.repository;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import com.ticket.dojo.backdeepfamily.domain.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueRepository extends JpaRepository<Queue, Long> {

    // Token으로 Queue 조회
    // SELECT * FROM queue WHERE token = ?
    Optional<Queue> findByToken(String token);

    // 특정 상태의 Queue 개수 조회
    // SELECT count(*) FROM queue WHERE status = ?
    int countByStatus(Queue.QueueStatus status);

    // 특정 시간 이전에 들어온 특정 상태의 Queue 개수
    // SELECT count(*) FROM queue WHERE status = ? AND entered_at < ?
    int countByStatusAndEnteredAtBefore(Queue.QueueStatus queueStatus, LocalDateTime enteredAt);

    // WAITING 상태를 enteredAt 순으로 조회 (Pageable로 개수 제어)
    // SELECT * FROM queue WHERE status = ? ORDER BY entered_at ASC LIMIT ?
    List<Queue> findByStatusOrderByEnteredAtAsc(Queue.QueueStatus status, Pageable pageable);

    // 특정 사용자의 먼저 들어온 대기열 상태가 있는지 반환
    Optional<Queue> findByUserAndStatusIn(User user, List<Queue.QueueStatus> active);
}
