package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "execution_metrics_5min", uniqueConstraints = {
    @UniqueConstraint(name = "uk_metrics_5min_project_bucket", columnNames = {"projectId", "bucketTime"})
}, indexes = {
    @Index(name = "idx_metrics_5min_bucket", columnList = "bucketTime")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionMetrics5minEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private Instant bucketTime;

    @Builder.Default
    private int totalCount = 0;

    @Builder.Default
    private int successCount = 0;

    @Builder.Default
    private int errorCount = 0;

    @Builder.Default
    private int canceledCount = 0;

    @Builder.Default
    private long totalDurationMs = 0;

    @Builder.Default
    private int finishedCount = 0;
}
