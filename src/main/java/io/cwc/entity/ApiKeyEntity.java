package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "api_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, unique = true)
    private String keyHash;

    @Column(nullable = false)
    private String keyPrefix;

    private String userId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant expiresAt;
}
