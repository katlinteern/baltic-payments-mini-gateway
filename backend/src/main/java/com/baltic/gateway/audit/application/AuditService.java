package com.baltic.gateway.audit.application;

import java.time.Clock;
import com.baltic.gateway.audit.domain.AuditEvent;
import com.baltic.gateway.audit.domain.AuditLog;
import com.baltic.gateway.audit.infrastructure.AuditLogRepository;
import com.baltic.gateway.platform.web.CorrelationIdFilter;
import org.slf4j.MDC;
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

    /** Joins the caller's transaction: if it rolls back, the entry goes with it. */
    @Transactional
    public void record(AuditEvent event) {
        repository.save(new AuditLog(event, MDC.get(CorrelationIdFilter.MDC_KEY), clock.instant()));
    }

    /** Own transaction: the entry survives a rollback of the caller (rejections, failed logins). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditEvent event) {
        repository.save(new AuditLog(event, MDC.get(CorrelationIdFilter.MDC_KEY), clock.instant()));
    }
}
