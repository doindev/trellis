package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ChatAgentResponse {
    private String id;
    private String name;
    private String description;
    private String systemPrompt;
    private String icon;
    private String model;
    private Instant createdAt;
    private Instant updatedAt;
}
