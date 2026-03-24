package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Single-JSON bundle format for project export.
 * Contains the project definition and all its workflows inline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectExportBundle {

    @Builder.Default
    private String version = "1.0";

    private Instant exportedAt;
    private ProjectConfigFile project;
    private List<WorkflowConfigFile> workflows;
}
