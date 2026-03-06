package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.JsonObjectConverter;
import io.cwc.util.NanoId;

@Entity
@Table(name = "executions", indexes = {
    @Index(name = "idx_exec_workflow", columnList = "workflowId"),
    @Index(name = "idx_exec_status", columnList = "status"),
    @Index(name = "idx_exec_started", columnList = "startedAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String workflowId;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object workflowData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExecutionMode mode = ExecutionMode.MANUAL;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object resultData;

    private Instant startedAt;

    private Instant finishedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public enum ExecutionStatus {
        NEW, RUNNING, SUCCESS, ERROR, CANCELED, WAITING
    }

    public enum ExecutionMode {
        MANUAL, TRIGGER, WEBHOOK, POLLING, RETRY, INTERNAL
    }
}
