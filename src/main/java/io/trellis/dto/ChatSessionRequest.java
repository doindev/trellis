package io.trellis.dto;

import lombok.Data;

@Data
public class ChatSessionRequest {
    private String title;
    private String agentId;
}
