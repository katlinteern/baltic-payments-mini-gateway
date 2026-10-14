package lt.baltic.gateway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private String action;
    @Column(name = "user_id", updatable = false) private UUID userId;
    @Column(updatable = false) private String username;
    @Column(name = "ip_address", updatable = false) private String ipAddress;
    @Column(updatable = false) private String details;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AuditLog() {}
    public AuditLog(String action, UUID userId, String username, String ipAddress, String details) {
        this.action = action;
        this.userId = userId;
        this.username = username;
        this.ipAddress = ipAddress;
        this.details = details;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getAction() { return action; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getIpAddress() { return ipAddress; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
