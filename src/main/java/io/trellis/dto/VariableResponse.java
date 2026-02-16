package io.trellis.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VariableResponse {
    private String id;
    private String key;
    private String value;
    private String type;
}
