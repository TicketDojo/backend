package com.ticket.dojo.backdeepfamily.domain.auth.repository;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {
}
