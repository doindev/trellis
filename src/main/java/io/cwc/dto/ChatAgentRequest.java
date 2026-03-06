package io.cwc.dto;

import lombok.Data;

@Data
public class ChatAgentRequest {
    private String name;
    private String description;
    private String systemPrompt;
    private String icon;
    private String model;
}
