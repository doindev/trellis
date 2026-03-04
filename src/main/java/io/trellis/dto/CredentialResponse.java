package io.trellis.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class CredentialResponse {
    private String id;
    private String projectId;
    private String name;
    private String type;
    private Instant createdAt;
    private Instant updatedAt;
    private List<String> sharedWithProjectIds;
}
