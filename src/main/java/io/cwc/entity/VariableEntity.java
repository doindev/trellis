package io.cwc.entity;

import io.cwc.util.NanoId;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "variables", uniqueConstraints =
    @UniqueConstraint(columnNames = {"var_key", "project_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "var_key", nullable = false)
    private String key;

    @Column(name = "var_value", columnDefinition = "TEXT")
    private String value;

    @Builder.Default
    @Column(nullable = false)
    private String type = "string";

    private String projectId;

    @Column(name = "source_placeholder")
    private String sourcePlaceholder;
}
