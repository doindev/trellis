package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "agent_shares",
    uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "target_project_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentShareEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "agent_id", nullable = false)
    private String agentId;

    @Column(name = "target_project_id", nullable = false)
    private String targetProjectId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
