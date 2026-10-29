package com.abzed.template.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByPrtToken(String prtToken);
    Optional<PasswordResetToken> findTopByPrtUser_UsrEmailOrderByPrtExpiryDateDesc(String usrEmail);
    // Reset authorization created after a verified reset OTP; consumed by update_password.
    Optional<PasswordResetToken> findTopByPrtUser_UsrIdAndPrtUsedFalseOrderByPrtExpiryDateDesc(UUID usrId);
    void deleteByPrtUser_UsrId(UUID usrId);
}
