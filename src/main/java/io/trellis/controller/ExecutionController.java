package io.trellis.controller;

import io.trellis.dto.ExecutionListResponse;
import io.trellis.dto.ExecutionResponse;
import io.trellis.engine.WorkflowEngine;
import io.trellis.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionService executionService;
    private final WorkflowEngine workflowEngine;

    @GetMapping("/api/executions")
    public Page<ExecutionListResponse> list(
            @RequestParam(required = false) String workflowId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return executionService.listExecutions(workflowId, status, page, size);
    }

    @GetMapping("/api/executions/{id}")
    public ExecutionResponse get(@PathVariable String id) {
        return executionService.getExecution(id);
    }

    @DeleteMapping("/api/executions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        executionService.deleteExecution(id);
    }

    @PostMapping("/api/executions/{id}/stop")
    public Map<String, String> stop(@PathVariable String id) {
        workflowEngine.stopExecution(id);
        return Map.of("status", "stopped");
    }

    @PostMapping("/api/executions/{id}/retry")
    public Map<String, String> retry(@PathVariable String id) {
        ExecutionResponse execution = executionService.getExecution(id);
        String newExecutionId = workflowEngine.startExecution(execution.getWorkflowId(), null);
        return Map.of("executionId", newExecutionId);
    }

    @PostMapping("/api/workflows/{workflowId}/run")
    public Map<String, String> runWorkflow(
            @PathVariable String workflowId,
            @RequestBody(required = false) Map<String, Object> inputData) {
        String executionId = workflowEngine.startExecution(workflowId, inputData);
        return Map.of("executionId", executionId);
    }
}
