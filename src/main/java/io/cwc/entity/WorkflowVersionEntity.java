package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.JsonObjectConverter;
import io.cwc.util.NanoId;

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

    @Column
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

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object mcpInputSchema;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object mcpOutputSchema;

    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    @Builder.Default
    @Column(nullable = false)
    private Instant publishedAt = Instant.now();
}
