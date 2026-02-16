package io.trellis.controller;

import io.trellis.dto.WorkflowCreateRequest;
import io.trellis.dto.WorkflowResponse;
import io.trellis.dto.WorkflowUpdateRequest;
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

    @PostMapping("/{id}/activate")
    public WorkflowResponse activate(@PathVariable String id) {
        return workflowService.activateWorkflow(id);
    }

    @PostMapping("/{id}/deactivate")
    public WorkflowResponse deactivate(@PathVariable String id) {
        return workflowService.deactivateWorkflow(id);
    }
}
