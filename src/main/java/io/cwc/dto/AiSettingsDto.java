package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingsDto {
    private String provider;
    private String apiKey;
    private String model;
    private String baseUrl;
    private boolean enabled;
    private int maxToolIterations;
    private String defaultAgentId;
}
