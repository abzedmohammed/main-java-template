package com.abzed.template.common;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SystemLogRepository extends JpaRepository<SystemLog, UUID> {
}
