package io.trellis.controller;

import io.trellis.dto.*;
import io.trellis.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    public List<WorkflowResponse> list(@RequestParam(required = false) String projectId) {
        if (projectId != null) {
            return workflowService.listWorkflowsByProject(projectId);
        }
        return workflowService.listWorkflows();
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
        return workflowService.updateWorkflow(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        workflowService.deleteWorkflow(id);
    }

    @PostMapping("/{id}/publish")
    public WorkflowResponse publish(@PathVariable String id, @RequestBody PublishWorkflowRequest request) {
        return workflowService.publishWorkflow(id, request);
    }

    @PostMapping("/{id}/unpublish")
    public WorkflowResponse unpublish(@PathVariable String id) {
        return workflowService.unpublishWorkflow(id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse duplicate(@PathVariable String id) {
        return workflowService.duplicateWorkflow(id);
    }

    @PostMapping("/{id}/archive")
    public WorkflowResponse archive(@PathVariable String id) {
        return workflowService.archiveWorkflow(id);
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
        return workflowService.publishFromVersion(id, versionId);
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
}
