package com.abzed.template.auth;

import com.abzed.template.audit.BaseEntity;
import com.abzed.template.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;

import java.time.Instant;
import java.util.UUID;

/**
 * A short-lived, single-use one-time password issued for a pre-auth flow.
 * Only a keyed one-way hash of the code is persisted (see {@link OtpHasher});
 * the plaintext exists solely in the delivered message. A resend issues a
 * fresh code rather than replaying the old one.
 */
@Getter
@Setter
@Entity
@Table(name = "otp_tokens")
public class OtpToken extends BaseEntity {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID otpId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "otp_user_id", nullable = false)
    private User otpUser;

    @Column(nullable = false, length = 64)
    private String otpCodeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpPurpose otpPurpose;

    @Column(nullable = false)
    private Instant otpExpiryDate;

    @Column(nullable = false)
    private boolean otpConsumed = false;

    @Column(nullable = false)
    private int otpAttempts = 0;
}
