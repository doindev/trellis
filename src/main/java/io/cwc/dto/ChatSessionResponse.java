package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ChatSessionResponse {
    private String id;
    private String title;
    private String agentId;
    private Instant createdAt;
    private Instant updatedAt;
}
