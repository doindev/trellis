package io.trellis.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.entity.ExecutionEntity.ExecutionMode;
import io.trellis.entity.ExecutionEntity.ExecutionStatus;
import io.trellis.entity.WorkflowEntity;
import io.trellis.nodes.core.*;
import io.trellis.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowService workflowService;
    private final ExecutionService executionService;
    private final CredentialService credentialService;
    private final VariableService variableService;
    private final WebSocketService webSocketService;
    private final NodeRegistry nodeRegistry;
    private final ExpressionEvaluator expressionEvaluator;
    private final ObjectMapper objectMapper;

    private final Map<String, WorkflowExecutionState> runningExecutions = new ConcurrentHashMap<>();

    public String startExecution(String workflowId, Map<String, Object> inputData) {
        WorkflowEntity workflow = workflowService.findById(workflowId);

        Map<String, Object> workflowSnapshot = new LinkedHashMap<>();
        workflowSnapshot.put("id", workflow.getId());
        workflowSnapshot.put("name", workflow.getName());
        workflowSnapshot.put("nodes", workflow.getNodes());
        workflowSnapshot.put("connections", workflow.getConnections());

        var execution = executionService.createExecution(
                workflowId, workflowSnapshot, ExecutionMode.MANUAL);

        executeAsync(execution.getId(), workflow, inputData, NodeExecutionContext.ExecutionMode.MANUAL);
        return execution.getId();
    }

    public String startWebhookExecution(String workflowId, String triggerNodeId, Map<String, Object> webhookData) {
        WorkflowEntity workflow = workflowService.findById(workflowId);

        Map<String, Object> workflowSnapshot = new LinkedHashMap<>();
        workflowSnapshot.put("id", workflow.getId());
        workflowSnapshot.put("name", workflow.getName());
        workflowSnapshot.put("nodes", workflow.getNodes());
        workflowSnapshot.put("connections", workflow.getConnections());

        var execution = executionService.createExecution(
                workflowId, workflowSnapshot, ExecutionMode.WEBHOOK);

        Map<String, Object> triggerInput = new LinkedHashMap<>();
        triggerInput.put("triggerNodeId", triggerNodeId);
        triggerInput.put("webhookData", webhookData);

        executeAsync(execution.getId(), workflow, triggerInput, NodeExecutionContext.ExecutionMode.WEBHOOK);
        return execution.getId();
    }

    public void stopExecution(String executionId) {
        WorkflowExecutionState state = runningExecutions.get(executionId);
        if (state != null) {
            state.setCancelled(true);
        }
        executionService.stop(executionId);
    }

    @Async("workflowExecutor")
    protected void executeAsync(String executionId, WorkflowEntity workflow,
                                 Map<String, Object> inputData, NodeExecutionContext.ExecutionMode mode) {
        try {
            executionService.updateStatus(executionId, ExecutionStatus.RUNNING);

            WorkflowGraph graph = WorkflowGraph.parse(
                    workflow.getNodes(), workflow.getConnections(), objectMapper);

            WorkflowExecutionState state = new WorkflowExecutionState(
                    executionId, workflow.getId(), graph);
            runningExecutions.put(executionId, state);

            webSocketService.sendExecutionStarted(executionId, workflow.getId());

            List<String> order = graph.getTopologicalOrder();
            Map<String, String> variables = variableService.getAllVariablesAsMap();

            for (String nodeId : order) {
                if (state.isCancelled()) {
                    executionService.finish(executionId, ExecutionStatus.CANCELED,
                            state.buildResultData(), "Execution cancelled by user");
                    webSocketService.sendExecutionFinished(executionId, "CANCELED", state.buildResultData());
                    return;
                }

                WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
                if (graphNode == null || graphNode.isDisabled()) continue;

                Optional<NodeRegistry.NodeRegistration> regOpt = nodeRegistry.getNode(
                        graphNode.getType(), graphNode.getTypeVersion());
                if (regOpt.isEmpty()) {
                    regOpt = nodeRegistry.getNode(graphNode.getType());
                }
                if (regOpt.isEmpty()) {
                    log.warn("Node type not found: {}", graphNode.getType());
                    continue;
                }

                NodeRegistry.NodeRegistration registration = regOpt.get();
                NodeInterface nodeInstance = registration.getNodeInstance();

                List<Map<String, Object>> nodeInput = state.collectInputForNode(nodeId);

                if (nodeInput.isEmpty() && inputData != null && graph.getStartNode() != null
                        && graph.getStartNode().getId().equals(nodeId)) {
                    if (inputData.containsKey("webhookData")) {
                        nodeInput = List.of(Map.of("json", inputData.get("webhookData")));
                    } else {
                        nodeInput = List.of(Map.of("json", inputData));
                    }
                }

                Map<String, Object> resolvedParams = resolveParameters(
                        graphNode.getParameters(), nodeInput, state, variables, executionId);

                Map<String, Object> credentials = resolveCredentials(graphNode.getCredentials());

                NodeExecutionContext context = NodeExecutionContext.builder()
                        .executionId(executionId)
                        .workflowId(workflow.getId())
                        .nodeId(nodeId)
                        .nodeType(graphNode.getType())
                        .nodeVersion(graphNode.getTypeVersion())
                        .inputData(nodeInput)
                        .parameters(resolvedParams)
                        .credentials(credentials)
                        .staticData(new HashMap<>())
                        .workflowStaticData(state.getWorkflowStaticData())
                        .executionMode(mode)
                        .continueOnFail(graphNode.isContinueOnFail())
                        .build();

                WorkflowExecutionState.NodeExecutionMetadata meta = new WorkflowExecutionState.NodeExecutionMetadata();
                meta.setNodeId(nodeId);
                meta.setNodeName(graphNode.getName());
                meta.setStartedAt(Instant.now());
                meta.setStatus("running");
                state.getNodeMetadata().put(nodeId, meta);

                try {
                    nodeInstance.beforeExecute(context);
                    NodeExecutionResult result = nodeInstance.execute(context);
                    nodeInstance.afterExecute(context, result);

                    meta.setFinishedAt(Instant.now());

                    if (result.getError() != null) {
                        meta.setStatus("error");
                        meta.setErrorMessage(result.getError().getMessage());

                        if (!graphNode.isContinueOnFail()) {
                            state.storeOutput(nodeId, result.getOutput() != null ?
                                    result.getOutput() : List.of(List.of()));
                            executionService.finish(executionId, ExecutionStatus.ERROR,
                                    state.buildResultData(), result.getError().getMessage());
                            webSocketService.sendExecutionFinished(
                                    executionId, "ERROR", state.buildResultData());
                            return;
                        }

                        List<Map<String, Object>> errorItems = List.of(
                                Map.of("json", Map.of("error", result.getError().getMessage())));
                        state.storeOutput(nodeId, List.of(errorItems));
                    } else {
                        meta.setStatus("success");
                        List<List<Map<String, Object>>> output = result.getOutput();
                        if (output == null) output = List.of(List.of());
                        state.storeOutput(nodeId, output);
                    }

                    if (result.getStaticData() != null) {
                        state.getWorkflowStaticData().putAll(result.getStaticData());
                    }

                    webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                            state.getNodeOutputs().get(nodeId));

                } catch (Exception e) {
                    meta.setFinishedAt(Instant.now());
                    meta.setStatus("error");
                    meta.setErrorMessage(e.getMessage());

                    log.error("Node execution failed: {} ({})", graphNode.getName(), nodeId, e);

                    if (graphNode.isContinueOnFail()) {
                        List<Map<String, Object>> errorItems = List.of(
                                Map.of("json", Map.of("error", e.getMessage())));
                        state.storeOutput(nodeId, List.of(errorItems));
                        webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                                state.getNodeOutputs().get(nodeId));
                    } else {
                        executionService.finish(executionId, ExecutionStatus.ERROR,
                                state.buildResultData(), e.getMessage());
                        webSocketService.sendExecutionFinished(
                                executionId, "ERROR", state.buildResultData());
                        return;
                    }
                }
            }

            executionService.finish(executionId, ExecutionStatus.SUCCESS,
                    state.buildResultData(), null);
            webSocketService.sendExecutionFinished(executionId, "SUCCESS", state.buildResultData());

        } catch (Exception e) {
            log.error("Workflow execution failed: {}", executionId, e);
            executionService.finish(executionId, ExecutionStatus.ERROR, null, e.getMessage());
            webSocketService.sendExecutionFinished(executionId, "ERROR",
                    Map.of("error", e.getMessage()));
        } finally {
            runningExecutions.remove(executionId);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveParameters(Map<String, Object> parameters,
                                                    List<Map<String, Object>> inputItems,
                                                    WorkflowExecutionState state,
                                                    Map<String, String> variables,
                                                    String executionId) {
        if (parameters == null) return Map.of();

        Map<String, Object> currentItemData = Map.of();
        if (inputItems != null && !inputItems.isEmpty()) {
            Map<String, Object> first = inputItems.get(0);
            Object json = first.get("json");
            if (json instanceof Map) {
                currentItemData = (Map<String, Object>) json;
            }
        }

        ExpressionEvaluator.ExpressionContext ctx = ExpressionEvaluator.ExpressionContext.builder()
                .currentItemData(currentItemData)
                .inputItems(inputItems)
                .variables(variables)
                .executionId(executionId)
                .runIndex(0)
                .build();

        Object resolved = expressionEvaluator.resolveExpressions(parameters, ctx);
        if (resolved instanceof Map) {
            return (Map<String, Object>) resolved;
        }
        return parameters;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveCredentials(Map<String, Object> credentialRefs) {
        if (credentialRefs == null || credentialRefs.isEmpty()) return Map.of();

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : credentialRefs.entrySet()) {
            Object value = entry.getValue();
            String credentialId = null;
            if (value instanceof Map) {
                credentialId = (String) ((Map<String, Object>) value).get("id");
            } else if (value instanceof String) {
                credentialId = (String) value;
            }

            if (credentialId != null) {
                try {
                    Map<String, Object> data = credentialService.getDecryptedData(credentialId);
                    resolved.putAll(data);
                } catch (Exception e) {
                    log.warn("Failed to resolve credential {}: {}", credentialId, e.getMessage());
                }
            }
        }
        return resolved;
    }
}
