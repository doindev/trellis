package io.cwc.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ProjectUpdateRequest {
    private String name;
    private String description;
    private Map<String, String> icon;
    private String contextPath;
}
