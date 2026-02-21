package io.trellis.entity;

import io.trellis.util.JsonObjectConverter;
import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "waits", indexes = {
    @Index(name = "idx_wait_status_resume", columnList = "status, resumeAt"),
    @Index(name = "idx_wait_execution", columnList = "executionId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String executionId;

    @Column(nullable = false)
    private String workflowId;

    @Column(nullable = false)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitType waitType;

    private Instant resumeAt;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object formDefinition;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object executionState;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WaitStatus status = WaitStatus.WAITING;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resumedAt;

    @Version
    private Long version;

    public enum WaitType {
        FORM, WEBHOOK, TIME_INTERVAL, SPECIFIC_TIME
    }

    public enum WaitStatus {
        WAITING, RESUMED, TIMED_OUT, CANCELLED
    }
}
