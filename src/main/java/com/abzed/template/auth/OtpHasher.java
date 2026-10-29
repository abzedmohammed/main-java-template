package com.abzed.template.auth;

import com.abzed.template.config.AuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * One-way keyed hashing for OTP codes. A 4-digit code has only 10,000 possible values, so a
 * plain hash would be reversed instantly if the table leaked. Instead the code is HMAC-SHA256'd
 * with a server-side secret (never stored in the DB) and bound to the user and purpose, so a
 * leaked row can neither be reversed nor replayed for another user/flow.
 */
@Component
public class OtpHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_LENGTH = 32;

    private final byte[] secret;

    @Autowired
    public OtpHasher(AuthProperties authProperties) {
        this(authProperties.getOtp().getSecret());
    }

    OtpHasher(String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.auth.otp.secret (APP_OTP_SECRET) must be set to at least " + MIN_SECRET_LENGTH + " characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(UUID usrId, OtpPurpose purpose, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] digest = mac.doFinal((usrId + ":" + purpose.name() + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash OTP", e);
        }
    }

    /** Constant-time comparison of a submitted code against a stored hash. */
    public boolean matches(UUID usrId, OtpPurpose purpose, String code, String storedHash) {
        if (code == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(usrId, purpose, code).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
