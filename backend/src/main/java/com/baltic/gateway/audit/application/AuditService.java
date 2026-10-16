package lt.baltic.gateway.audit.application;

import java.util.UUID;
import lt.baltic.gateway.audit.domain.AuditLog;
import lt.baltic.gateway.audit.infrastructure.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    public AuditService(AuditLogRepository repository) { this.repository = repository; }
    @Transactional
    public void record(String action, UUID userId, String username, String ip, String details) {
        repository.save(new AuditLog(action, userId, username, ip, details));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(String action, UUID userId, String username, String ip, String details) {
        repository.save(new AuditLog(action, userId, username, ip, details));
    }
    @Transactional(readOnly = true)
    public Page<AuditLog> list(Pageable pageable) { return repository.findAll(pageable); }
}
