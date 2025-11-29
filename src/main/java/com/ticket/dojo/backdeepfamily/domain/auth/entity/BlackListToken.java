package com.ticket.dojo.backdeepfamily.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklist_token")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlackListToken {

    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 500)
    private String accessToken;

    @Column(nullable = false)
    private LocalDateTime expiration;
}
