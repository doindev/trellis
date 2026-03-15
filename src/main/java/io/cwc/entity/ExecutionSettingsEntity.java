package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "execution_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionSettingsEntity {

    @Id
    @NanoId
    private String id;

    @Builder.Default
    @Column(nullable = false)
    private String saveExecutionProgress = "yes";

    @Builder.Default
    @Column(nullable = false)
    private String saveManualExecutions = "yes";

    @Builder.Default
    @Column(nullable = false)
    private int executionTimeout = -1;

    private String errorWorkflow;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
