package io.trellis.entity;

import io.trellis.util.JsonObjectConverter;
import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "data_table_rows", indexes = {
    @Index(name = "idx_dtr_table", columnList = "dataTableId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataTableRowEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String dataTableId;

    @Lob
    @Column(columnDefinition = "TEXT")
    @Convert(converter = JsonObjectConverter.class)
    private Object rowData;

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
