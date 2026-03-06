package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ApiKeyResponse {
    private String id;
    private String label;
    private String keyPrefix;
    private String userId;
    private Instant createdAt;
    private Instant expiresAt;
    /** Only set when the key is first created. */
    private String apiKey;
}
