package io.trellis.entity;

import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "variables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "var_key", nullable = false, unique = true)
    private String key;

    @Column(name = "var_value", columnDefinition = "TEXT")
    private String value;

    @Builder.Default
    @Column(nullable = false)
    private String type = "string";

    private String projectId;
}
