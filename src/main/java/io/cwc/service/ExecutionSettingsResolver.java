package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.dto.ExecutionSettingsDto;
import io.cwc.dto.ResolvedExecutionSettings;
import io.cwc.entity.ProjectEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.repository.ProjectRepository;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionSettingsResolver {

    private final ExecutionSettingsService executionSettingsService;
    private final ProjectRepository projectRepository;

    /**
     * Resolves execution settings by merging: workflow -> project -> application.
     */
    public ResolvedExecutionSettings resolve(WorkflowEntity workflow) {
        ExecutionSettingsDto appSettings = executionSettingsService.getSettings();
        Map<String, Object> projectSettings = getProjectSettings(workflow.getProjectId());
        Map<String, Object> workflowSettings = getWorkflowSettings(workflow.getSettings());

        return ResolvedExecutionSettings.builder()
                .saveExecutionProgress(resolveYesNo(workflowSettings, projectSettings, appSettings.getSaveExecutionProgress(), "saveExecutionProgress"))
                .saveManualExecutions(resolveYesNo(workflowSettings, projectSettings, appSettings.getSaveManualExecutions(), "saveManualExecutions"))
                .executionTimeout(resolveTimeout(workflowSettings, projectSettings, appSettings.getExecutionTimeout()))
                .errorWorkflow(resolveErrorWorkflow(workflowSettings, projectSettings, appSettings.getErrorWorkflow()))
                .build();
    }

    /**
     * Resolves execution settings for a given project ID and workflow ID.
     * Returns the resolved settings along with the source of each value.
     */
    public Map<String, Object> resolveWithSources(String workflowId, String projectId, Map<String, Object> workflowSettings) {
        ExecutionSettingsDto appSettings = executionSettingsService.getSettings();
        Map<String, Object> projectSettings = getProjectSettings(projectId);
        Map<String, Object> wfSettings = workflowSettings != null ? workflowSettings : Map.of();

        return Map.of(
                "saveExecutionProgress", resolveWithSource(wfSettings, projectSettings, appSettings.getSaveExecutionProgress(), "saveExecutionProgress", true),
                "saveManualExecutions", resolveWithSource(wfSettings, projectSettings, appSettings.getSaveManualExecutions(), "saveManualExecutions", true),
                "executionTimeout", resolveTimeoutWithSource(wfSettings, projectSettings, appSettings.getExecutionTimeout()),
                "errorWorkflow", resolveErrorWorkflowWithSource(wfSettings, projectSettings, appSettings.getErrorWorkflow())
        );
    }

    private boolean resolveYesNo(Map<String, Object> wfSettings, Map<String, Object> projectSettings, String appValue, String key) {
        // Check workflow first
        String wfVal = getStringValue(wfSettings, key);
        if (wfVal != null && !"default".equals(wfVal)) {
            return "yes".equals(wfVal);
        }
        // Check project
        String projVal = getStringValue(projectSettings, key);
        if (projVal != null && !"default".equals(projVal)) {
            return "yes".equals(projVal);
        }
        // Fall back to app
        return "yes".equals(appValue);
    }

    private int resolveTimeout(Map<String, Object> wfSettings, Map<String, Object> projectSettings, int appValue) {
        // Check workflow first
        Integer wfVal = getIntValue(wfSettings, "executionTimeout");
        if (wfVal != null && wfVal > 0) {
            return wfVal;
        }
        // Check project
        Integer projVal = getIntValue(projectSettings, "executionTimeout");
        if (projVal != null && projVal > 0) {
            return projVal;
        }
        // Fall back to app
        return appValue;
    }

    private String resolveErrorWorkflow(Map<String, Object> wfSettings, Map<String, Object> projectSettings, String appValue) {
        // Check workflow first
        String wfVal = getStringValue(wfSettings, "errorWorkflow");
        if (wfVal != null && !wfVal.isEmpty()) {
            return wfVal;
        }
        // Check project
        String projVal = getStringValue(projectSettings, "errorWorkflow");
        if (projVal != null && !projVal.isEmpty()) {
            return projVal;
        }
        // Fall back to app
        return appValue;
    }

    private Map<String, Object> resolveWithSource(Map<String, Object> wfSettings, Map<String, Object> projectSettings, String appValue, String key, boolean isYesNo) {
        String wfVal = getStringValue(wfSettings, key);
        if (wfVal != null && !"default".equals(wfVal)) {
            return Map.of("value", isYesNo ? "yes".equals(wfVal) : wfVal, "source", "workflow");
        }
        String projVal = getStringValue(projectSettings, key);
        if (projVal != null && !"default".equals(projVal)) {
            return Map.of("value", isYesNo ? "yes".equals(projVal) : projVal, "source", "project");
        }
        return Map.of("value", isYesNo ? "yes".equals(appValue) : (appValue != null ? appValue : ""), "source", "application");
    }

    private Map<String, Object> resolveTimeoutWithSource(Map<String, Object> wfSettings, Map<String, Object> projectSettings, int appValue) {
        Integer wfVal = getIntValue(wfSettings, "executionTimeout");
        if (wfVal != null && wfVal > 0) {
            return Map.of("value", wfVal, "source", "workflow");
        }
        Integer projVal = getIntValue(projectSettings, "executionTimeout");
        if (projVal != null && projVal > 0) {
            return Map.of("value", projVal, "source", "project");
        }
        return Map.of("value", appValue, "source", "application");
    }

    private Map<String, Object> resolveErrorWorkflowWithSource(Map<String, Object> wfSettings, Map<String, Object> projectSettings, String appValue) {
        String wfVal = getStringValue(wfSettings, "errorWorkflow");
        if (wfVal != null && !wfVal.isEmpty()) {
            return Map.of("value", wfVal, "source", "workflow");
        }
        String projVal = getStringValue(projectSettings, "errorWorkflow");
        if (projVal != null && !projVal.isEmpty()) {
            return Map.of("value", projVal, "source", "project");
        }
        return Map.of("value", appValue != null ? appValue : "", "source", "application");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getProjectSettings(String projectId) {
        if (projectId == null) return Map.of();
        return projectRepository.findById(projectId)
                .map(ProjectEntity::getSettings)
                .filter(s -> s instanceof Map)
                .map(s -> (Map<String, Object>) s)
                .orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getWorkflowSettings(Object settings) {
        if (settings instanceof Map) {
            return (Map<String, Object>) settings;
        }
        return Map.of();
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : null;
    }

    private Integer getIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return null;
    }
}
