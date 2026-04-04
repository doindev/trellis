package io.cwc.entity;

import io.cwc.util.NanoIdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;

/**
 * API token for securing MCP server endpoints.
 * Each user can create up to 5 tokens per MCP endpoint URL.
 * The raw token value is shown only once at creation time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mcp_api_tokens", indexes = {
    @Index(name = "idx_mcp_token_hash", columnList = "tokenHash", unique = true),
    @Index(name = "idx_mcp_token_user_endpoint", columnList = "userId, mcpEndpointId")
})
public class McpApiTokenEntity {

    @Id
    @GeneratedValue(generator = "nanoid")
    @GenericGenerator(name = "nanoid", type = NanoIdGenerator.class)
    private String id;

    /** User-provided name for reference */
    @Column(nullable = false)
    private String name;

    /** SHA-256 hash of the token value (never store raw) */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    /** First 8 characters of the token for display (e.g. "mcp_a1b2...") */
    @Column(nullable = false, length = 16)
    private String tokenPrefix;

    /** Owner of this token */
    @Column(nullable = false)
    private String userId;

    /** The MCP endpoint this token grants access to */
    @Column(nullable = false)
    private String mcpEndpointId;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Null means no expiration */
    private Instant expiresAt;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
