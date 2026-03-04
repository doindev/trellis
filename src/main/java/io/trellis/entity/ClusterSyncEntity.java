package io.trellis.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "cluster_sync")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterSyncEntity {

    @Id
    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant updatedAt;
}
