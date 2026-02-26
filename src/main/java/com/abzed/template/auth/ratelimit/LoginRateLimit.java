package com.abzed.template.auth.ratelimit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "login_rate_limits")
public class LoginRateLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private int attempts;

    private Instant blockedUntil;

    @Column(nullable = false)
    private Instant updatedAt;
}
