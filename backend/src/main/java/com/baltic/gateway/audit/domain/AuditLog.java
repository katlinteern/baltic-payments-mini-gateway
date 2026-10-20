package com.baltic.gateway.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false) private AuditAction action;
    @Column(name = "user_id", updatable = false) private UUID userId;
    @Column(updatable = false) private String username;
    @Column(name = "ip_address", updatable = false) private String ipAddress;
    @Column(updatable = false) private String details;
    /** Ties the entry to the request's log lines and to the {@code correlationId} the client received. */
    @Column(name = "correlation_id", updatable = false) private String correlationId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AuditLog() {}
    public AuditLog(AuditEvent event, String correlationId, Instant createdAt) {
        this.action = event.action();
        this.userId = event.userId();
        this.username = event.username();
        this.ipAddress = event.ipAddress();
        this.details = event.details();
        this.correlationId = correlationId;
        this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public AuditAction getAction() { return action; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getIpAddress() { return ipAddress; }
    public String getDetails() { return details; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
}
