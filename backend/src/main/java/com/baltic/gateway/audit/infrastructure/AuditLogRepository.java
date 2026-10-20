package com.baltic.gateway.audit.infrastructure;

import com.baltic.gateway.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
