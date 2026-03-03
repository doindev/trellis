package io.trellis.entity;

import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "projects", indexes = {
    @Index(name = "idx_project_type", columnList = "type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectType type;

    @Column(columnDefinition = "TEXT")
    private String icon;

    @Column(length = 512)
    private String description;

    @Column(unique = true, length = 100)
    private String contextPath;

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

    public enum ProjectType {
        PERSONAL, TEAM
    }
}
