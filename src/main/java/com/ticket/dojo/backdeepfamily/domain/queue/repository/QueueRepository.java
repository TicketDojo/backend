package com.ticket.dojo.backdeepfamily.domain.queue.repository;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QueueRepository extends JpaRepository<Queue, Long> {

    // Token으로 Queue 조회
    Optional<Queue> findByToken(String token);

    // 특정 상태의 Queue 개수 조회
    int countByStatus(Queue.QueueStatus status);
}
