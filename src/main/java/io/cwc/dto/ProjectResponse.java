package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProjectResponse {
    private String id;
    private String name;
    private String type;
    private Map<String, String> icon;
    private String description;
    private String contextPath;
    private List<ProjectMemberResponse> members;
    private long workflowCount;
    private long credentialCount;
    private Instant createdAt;
    private Instant updatedAt;
}
