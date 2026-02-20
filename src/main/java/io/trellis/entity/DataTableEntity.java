package io.trellis.entity;

import io.trellis.util.JsonObjectConverter;
import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "data_tables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "table_name", nullable = false)
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    @Convert(converter = JsonObjectConverter.class)
    private Object columnDefinitions;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
