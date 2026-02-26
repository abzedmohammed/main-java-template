package com.abzed.template.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findTopByUserEmailOrderByExpiryDateDesc(String email);
    void deleteByUserId(UUID userId);
}
