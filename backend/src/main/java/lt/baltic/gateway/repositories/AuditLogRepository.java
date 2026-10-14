package lt.baltic.gateway.repositories;

import lt.baltic.gateway.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
