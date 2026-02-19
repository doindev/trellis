package io.trellis.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ProjectUpdateRequest {
    private String name;
    private String description;
    private Map<String, String> icon;
}
