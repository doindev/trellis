package io.cwc.dto;

import lombok.Data;

@Data
public class ChatSessionRequest {
    private String title;
    private String agentId;
    private String workflowId;
}
