package com.abzed.template.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
    Optional<EmailTemplate> findByEmlCodeAndEmlActiveTrue(String emlCode);
    Optional<EmailTemplate> findByEmlCode(String emlCode);
}
