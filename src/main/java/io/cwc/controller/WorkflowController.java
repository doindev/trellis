package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.*;
import io.cwc.service.ClusterSyncService;
import io.cwc.service.CwcMcpServerManager;
import io.cwc.service.WorkflowService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ClusterSyncService clusterSyncService;
    private final Optional<CwcMcpServerManager> mcpServerManager;

    @GetMapping
    public List<WorkflowResponse> list(@RequestParam(required = false) String projectId,
                                       @RequestParam(required = false) String type) {
        List<WorkflowResponse> result;
        if (projectId != null) {
            result = workflowService.listWorkflowsByProject(projectId);
        } else {
            result = workflowService.listWorkflows();
        }
        if (type != null) {
            result = result.stream().filter(w -> type.equals(w.getType())).toList();
        }
        return result;
    }

    @GetMapping("/agents/visible")
    public List<WorkflowResponse> listVisibleAgents(@RequestParam String projectId) {
        return workflowService.listAgentsVisibleToProject(projectId);
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable String id) {
        return workflowService.getWorkflow(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse create(@RequestBody WorkflowCreateRequest request) {
        return workflowService.createWorkflow(request);
    }

    @PutMapping("/{id}")
    public WorkflowResponse update(@PathVariable String id, @RequestBody WorkflowUpdateRequest request) {
        WorkflowResponse response = workflowService.updateWorkflow(id, request);
        mcpServerManager.filter(CwcMcpServerManager::isRunning)
                .filter(m -> request.getMcpEnabled() != null
                        || request.getMcpDescription() != null
                        || request.getMcpInputSchema() != null
                        || request.getMcpOutputSchema() != null
                        || request.getName() != null
                        || request.getDescription() != null)
                .ifPresent(CwcMcpServerManager::refreshTools);
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        workflowService.deleteWorkflow(id);
        notifyWorkflowChanged();
    }

    @PostMapping("/{id}/publish")
    public WorkflowResponse publish(@PathVariable String id, @RequestBody PublishWorkflowRequest request) {
        WorkflowResponse response = workflowService.publishWorkflow(id, request);
        notifyWorkflowChanged();
        return response;
    }

    @PostMapping("/{id}/unpublish")
    public WorkflowResponse unpublish(@PathVariable String id) {
        WorkflowResponse response = workflowService.unpublishWorkflow(id);
        notifyWorkflowChanged();
        return response;
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse duplicate(@PathVariable String id) {
        return workflowService.duplicateWorkflow(id);
    }

    @PostMapping("/{id}/archive")
    public WorkflowResponse archive(@PathVariable String id) {
        WorkflowResponse response = workflowService.archiveWorkflow(id);
        notifyWorkflowChanged();
        return response;
    }

    @GetMapping("/{id}/versions")
    public Page<WorkflowVersionResponse> getVersions(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String filter) {
        return workflowService.getWorkflowVersionsPaged(id, page, size, filter);
    }

    @GetMapping("/{id}/versions/{versionId}")
    public WorkflowVersionResponse getVersion(@PathVariable String id, @PathVariable String versionId) {
        return workflowService.getWorkflowVersion(id, versionId);
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    public WorkflowResponse publishFromVersion(@PathVariable String id, @PathVariable String versionId) {
        WorkflowResponse response = workflowService.publishFromVersion(id, versionId);
        notifyWorkflowChanged();
        return response;
    }

    @PostMapping("/{id}/versions/{versionId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse cloneFromVersion(@PathVariable String id, @PathVariable String versionId) {
        return workflowService.cloneFromVersion(id, versionId);
    }

    @PutMapping("/{id}/tags")
    public WorkflowResponse updateTags(@PathVariable String id, @RequestBody List<String> tagIds) {
        return workflowService.updateWorkflowTags(id, tagIds);
    }

    @PostMapping("/{id}/move")
    public WorkflowResponse move(@PathVariable String id, @RequestBody WorkflowMoveRequest request) {
        return workflowService.moveWorkflow(id, request);
    }

    @GetMapping("/{id}/shares")
    public List<WorkflowShareResponse> getShares(@PathVariable String id) {
        return workflowService.getShares(id);
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowShareResponse addShare(@PathVariable String id, @RequestBody WorkflowShareRequest request) {
        return workflowService.addShare(id, request);
    }

    @PatchMapping("/{id}/shares/{shareId}")
    public WorkflowShareResponse updateShare(@PathVariable String id, @PathVariable String shareId,
                                             @RequestBody WorkflowShareRequest request) {
        return workflowService.updateShare(id, shareId, request);
    }

    @DeleteMapping("/{id}/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeShare(@PathVariable String id, @PathVariable String shareId) {
        workflowService.removeShare(id, shareId);
    }

    // --- Agent project shares ---

    @GetMapping("/{id}/project-shares")
    public List<Map<String, String>> getAgentProjectShares(@PathVariable String id) {
        return workflowService.getAgentProjectShares(id);
    }

    @PostMapping("/{id}/project-shares")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> shareAgentWithProject(@PathVariable String id,
                                                      @RequestBody Map<String, String> request) {
        return workflowService.shareAgentWithProject(id, request.get("targetProjectId"));
    }

    @DeleteMapping("/{id}/project-shares/{targetProjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshareAgentFromProject(@PathVariable String id, @PathVariable String targetProjectId) {
        workflowService.unshareAgentFromProject(id, targetProjectId);
    }

    private void notifyWorkflowChanged() {
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_WEBHOOKS);
        mcpServerManager.filter(CwcMcpServerManager::isRunning)
                .ifPresent(CwcMcpServerManager::refreshTools);
    }
}
