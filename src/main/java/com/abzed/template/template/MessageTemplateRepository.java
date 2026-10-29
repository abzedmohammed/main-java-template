package com.abzed.template.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {
    Optional<MessageTemplate> findByMtpCodeAndMtpActiveTrue(String mtpCode);
    Optional<MessageTemplate> findByMtpCode(String mtpCode);
}
