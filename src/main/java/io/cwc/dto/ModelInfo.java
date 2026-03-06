package io.cwc.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ModelInfo {
    private String id;
    private String name;
}
