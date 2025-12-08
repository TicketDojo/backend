package com.ticket.dojo.backdeepfamily.domain.queue.repository;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
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

    // WAITING 상태를 enteredAt 순으로 조회 (최대 10개)
    // SELECT * FROM queue WHERE status = ? ORDER BY entered_at ASC LIMIT 10
    List<Queue> findTop10ByStatusOrderByEnteredAtAsc(Queue.QueueStatus status);

    // expiresAt이 현재 시간보다 이전인 ACTIVE Queue 조회
    // SELECT * FROM queue WHERE status = ? AND expired_at < ?
    List<Queue> findByStatusAndExpiresAtBefore(Queue.QueueStatus status, LocalDateTime now);

    // 특정 유저의 특정 상태들에 해당하는 Queue 조회
    // SELECT * FROM queue WHERE user_id = ? AND status IN (?, ?)
    List<Queue> findByUser_UserIdAndStatusIn(Long userId, List<Queue.QueueStatus> statuses);

    // 특정 유저의 특정 상태들에 해당하는 Queue 삭제
    // DELETE FROM queue WHERE user_id = ? AND status IN (?, ?)
    void deleteByUser_UserIdAndStatusIn(Long userId, List<Queue.QueueStatus> statuses);
}
