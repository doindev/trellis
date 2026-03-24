package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import io.cwc.util.JsonObjectConverter;
import io.cwc.util.NanoId;

@Entity
@Table(name = "workflows", indexes = {
    @Index(name = "idx_workflow_project", columnList = "projectId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEntity {

    @Id
    @NanoId
    private String id;

    private String projectId;

    @Column(name = "config_id")
    private String configId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private String type = "WORKFLOW";  // "WORKFLOW" or "AGENT"

    private String icon;  // emoji/icon for agent display

    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;

    @Builder.Default
    @Column(nullable = false)
    private int currentVersion = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean versionIsDirty = false;

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
    private Object staticData;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object pinData;

    @Builder.Default
    @Column(name = "mcp_enabled", nullable = false)
    private boolean mcpEnabled = false;

    @Column(columnDefinition = "TEXT")
    private String mcpDescription;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object mcpInputSchema;

    @Lob
    @Convert(converter = JsonObjectConverter.class)
    @Column(columnDefinition = "TEXT")
    private Object mcpOutputSchema;

    @Builder.Default
    @Column(name = "swagger_enabled", nullable = false)
    private boolean swaggerEnabled = false;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "workflow_tags",
        joinColumns = @JoinColumn(name = "workflow_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<TagEntity> tags = new LinkedHashSet<>();

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
