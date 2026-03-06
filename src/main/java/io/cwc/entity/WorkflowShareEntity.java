package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "workflow_shares",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_id", "user_id"}),
        indexes = {
                @Index(name = "idx_wf_share_workflow", columnList = "workflow_id"),
                @Index(name = "idx_wf_share_user", columnList = "user_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowShareEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "workflow_id", nullable = false)
    private String workflowId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SharePermission permission;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum SharePermission {
        VIEW,
        EDIT
    }
}
