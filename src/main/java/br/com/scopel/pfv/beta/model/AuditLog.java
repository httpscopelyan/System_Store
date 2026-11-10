package br.com.scopel.pfv.beta.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @ManyToOne
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @Column(name = "actor_name", nullable = false, updatable = false, length = 50)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 30)
    private AuditEntityType entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(name = "previous_value", updatable = false)
    private String previousValue;

    @Column(name = "new_value", updatable = false)
    private String newValue;

    protected AuditLog() {
    }

    public AuditLog(LocalDateTime occurredAt, User actor, String actorName, AuditAction action,
                    AuditEntityType entityType, Long entityId, String description, String previousValue,
                    String newValue) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.actorName = actorName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.description = description;
        this.previousValue = previousValue;
        this.newValue = newValue;
    }
}
