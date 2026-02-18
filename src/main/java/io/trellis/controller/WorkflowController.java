package io.trellis.controller;

import io.trellis.dto.*;
import io.trellis.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping
    public List<WorkflowResponse> list() {
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
    public List<WorkflowVersionResponse> getVersions(@PathVariable String id) {
        return workflowService.getWorkflowVersions(id);
    }
}
