package io.trellis.entity;

import io.trellis.util.JsonObjectConverter;
import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "workflow_versions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowVersionEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String workflowId;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false)
    private String versionName;

    @Column
    private String description;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object nodes;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object connections;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object settings;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object pinData;

    @Builder.Default
    @Column(nullable = false)
    private Instant publishedAt = Instant.now();
}
