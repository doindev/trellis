package io.cwc.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ChatMessageRequest {
    private String content;
    private Map<String, Object> canvasState;
}
