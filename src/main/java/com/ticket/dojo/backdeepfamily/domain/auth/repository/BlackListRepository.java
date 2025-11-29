package com.ticket.dojo.backdeepfamily.domain.auth.repository;

import com.ticket.dojo.backdeepfamily.domain.auth.entity.BlackListToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlackListRepository extends JpaRepository<BlackListToken, Long> {
}
