package com.abzed.template.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    Optional<OtpToken> findTopByOtpUser_UsrIdAndOtpConsumedFalseOrderByCreatedAtDesc(UUID usrId);

    Optional<OtpToken> findTopByOtpUser_UsrIdOrderByCreatedAtDesc(UUID usrId);

    void deleteByOtpUser_UsrId(UUID usrId);
}
