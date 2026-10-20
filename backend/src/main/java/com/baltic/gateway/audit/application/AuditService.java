package com.baltic.gateway.audit.application;

import java.time.Clock;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.audit.domain.AuditLog;
import com.baltic.gateway.audit.infrastructure.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Written in the caller's transaction: if the surrounding work rolls back, the entry goes with it. */
    @Transactional
    public void record(AuditEvent event) {
        repository.save(new AuditLog(event, clock.instant()));
    }

    /** Written in its own transaction, so the entry survives a rollback of the caller (rejections, failed logins). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditEvent event) {
        repository.save(new AuditLog(event, clock.instant()));
    }
}
