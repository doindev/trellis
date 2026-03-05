package io.trellis.entity;

import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "trigger_locks", uniqueConstraints = {
    @UniqueConstraint(name = "uk_trigger_lock_wf_node", columnNames = {"workflowId", "nodeId"})
}, indexes = {
    @Index(name = "idx_trigger_lock_instance", columnList = "instanceId"),
    @Index(name = "idx_trigger_lock_expires", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerLockEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String workflowId;

    @Column(nullable = false)
    private String nodeId;

    @Column(nullable = false)
    private String instanceId;

    @Column(nullable = false)
    private Instant acquiredAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastHeartbeat;
}
