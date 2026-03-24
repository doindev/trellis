package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for config bootstrap / reload operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigReloadResult {

    private String mode;
    private int pathsScanned;
    @Builder.Default
    private List<String> discoveryModes = new ArrayList<>();

    private int settingsApplied;
    private int projectsCreated;
    private int projectsUpdated;
    private int projectsSkipped;
    private int projectsFailed;
    private int workflowsCreated;
    private int workflowsUpdated;
    private int workflowsSkipped;
    private int workflowsFailed;
    private int workflowsPublished;
    private int variablesApplied;
    private int credentialsApplied;
    private int cachesApplied;

    @Builder.Default
    private List<String> errors = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    @Builder.Default
    private List<String> adoptions = new ArrayList<>();

    public void addError(String error) {
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addAdoption(String adoption) {
        adoptions.add(adoption);
    }
}
