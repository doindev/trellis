package io.trellis.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ChatMessageResponse {
    private String id;
    private String sessionId;
    private String role;
    private String content;
    private Instant createdAt;
}
