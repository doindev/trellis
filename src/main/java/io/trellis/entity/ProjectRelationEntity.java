package io.trellis.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "project_relations")
@IdClass(ProjectRelationId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectRelationEntity {

    @Id
    private String projectId;

    @Id
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum ProjectRole {
        PROJECT_PERSONAL_OWNER,
        PROJECT_ADMIN,
        PROJECT_EDITOR,
        PROJECT_VIEWER
    }
}
