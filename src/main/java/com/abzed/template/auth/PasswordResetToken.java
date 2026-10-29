package com.abzed.template.auth;

import com.abzed.template.user.User;
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
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID prtId;

    @Column(nullable = false, unique = true, length = 150)
    private String prtToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prt_user_id", nullable = false)
    private User prtUser;

    @Column(nullable = false)
    private Instant prtExpiryDate;

    @Column(nullable = false)
    private boolean prtUsed = false;
}
