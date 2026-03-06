package io.cwc.dto;

import lombok.Data;

@Data
public class ProjectMemberRequest {
    private String userId;
    private String role;
}
