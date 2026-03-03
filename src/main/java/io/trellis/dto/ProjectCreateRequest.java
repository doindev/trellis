package io.trellis.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ProjectCreateRequest {
    private String name;
    private String description;
    private Map<String, String> icon;
    private String contextPath;
}
