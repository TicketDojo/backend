package com.ticket.dojo.backdeepfamily.domain.queue.repository;

import com.ticket.dojo.backdeepfamily.domain.queue.entity.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QueueRepository extends JpaRepository<Queue, Long> {
}
