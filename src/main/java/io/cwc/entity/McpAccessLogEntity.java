package io.cwc.entity;

import io.cwc.util.NanoIdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Access log for MCP endpoint requests.
 * Partitioned by date for efficient retention (drop data older than 90 days).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mcp_access_log", indexes = {
    @Index(name = "idx_mcp_log_date", columnList = "logDate"),
    @Index(name = "idx_mcp_log_user", columnList = "username"),
    @Index(name = "idx_mcp_log_endpoint", columnList = "endpointPath")
})
public class McpAccessLogEntity {

    @Id
    @GeneratedValue(generator = "nanoid")
    @GenericGenerator(name = "nanoid", type = NanoIdGenerator.class)
    private String id;

    /** Date partition key (YYYY-MM-DD) for retention management */
    @Column(nullable = false)
    private LocalDate logDate;

    /** Originating IP address (considers X-Forwarded-For, X-Real-IP, etc.) */
    @Column(nullable = false, length = 45)
    private String ipAddress;

    /** Username of the token owner (or "anonymous" if no token) */
    @Column(nullable = false)
    private String username;

    /** User-assigned token name */
    private String tokenName;

    /** The MCP endpoint path that was called */
    @Column(nullable = false)
    private String endpointPath;

    /** Full request URL */
    @Column(nullable = false, length = 2000)
    private String requestUrl;

    /** HTTP method */
    @Column(nullable = false, length = 10)
    private String httpMethod;

    /** Result: ALLOWED or DENIED */
    @Column(nullable = false, length = 10)
    private String status;

    /** HTTP response code (200, 401, 403, etc.) */
    private int responseCode;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();
}
