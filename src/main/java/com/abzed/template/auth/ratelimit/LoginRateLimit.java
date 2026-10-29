package com.abzed.template.auth.ratelimit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "login_rate_limits")
public class LoginRateLimit {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID lrlId;

    @Column(nullable = false, unique = true)
    private String lrlEmail;

    @Column(nullable = false)
    private int lrlAttempts;

    private Instant lrlBlockedUntil;

    @Column(nullable = false)
    private Instant lrlUpdatedAt;
}
