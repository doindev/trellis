package io.trellis.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.entity.ExecutionEntity.ExecutionMode;
import io.trellis.entity.ExecutionEntity.ExecutionStatus;
import io.trellis.entity.WaitEntity.WaitType;
import io.trellis.entity.WorkflowEntity;
import io.trellis.nodes.core.*;
import io.trellis.service.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final WaitService waitService;
    private final WaitPollerService waitPollerService;

    private final Map<String, WorkflowExecutionState> runningExecutions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Map<String, Object>>> pendingWebhookResponses = new ConcurrentHashMap<>();

    private static final ThreadLocal<Integer> SUB_WORKFLOW_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_SUB_WORKFLOW_DEPTH = 10;

    @PostConstruct
    void init() {
        waitPollerService.setResumeHandler(this::resumeFromWait);
    }

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

    /**
     * Start a webhook execution and return a future that will be completed with the
     * webhook response data (from a RespondToWebhook node or last node output).
     */
    public CompletableFuture<Map<String, Object>> startWebhookExecutionWithResponse(
            String workflowId, String triggerNodeId, Map<String, Object> webhookData) {
        WorkflowEntity workflow = workflowService.findById(workflowId);

        Map<String, Object> workflowSnapshot = new LinkedHashMap<>();
        workflowSnapshot.put("id", workflow.getId());
        workflowSnapshot.put("name", workflow.getName());
        workflowSnapshot.put("nodes", workflow.getNodes());
        workflowSnapshot.put("connections", workflow.getConnections());

        var execution = executionService.createExecution(
                workflowId, workflowSnapshot, ExecutionMode.WEBHOOK);

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingWebhookResponses.put(execution.getId(), future);

        Map<String, Object> triggerInput = new LinkedHashMap<>();
        triggerInput.put("triggerNodeId", triggerNodeId);
        triggerInput.put("webhookData", webhookData);

        executeAsync(execution.getId(), workflow, triggerInput, NodeExecutionContext.ExecutionMode.WEBHOOK);
        return future;
    }

    public void stopExecution(String executionId) {
        WorkflowExecutionState state = runningExecutions.get(executionId);
        if (state != null) {
            state.setCancelled(true);
        }
        waitService.cancelAllForExecution(executionId);
        executionService.stop(executionId);
    }

    /**
     * Execute a sub-workflow synchronously and return its last node's output.
     * Called by ExecuteWorkflowNode to run another workflow inline.
     * Includes recursion depth protection to prevent infinite loops.
     */
    public List<Map<String, Object>> executeSubWorkflow(String workflowId, List<Map<String, Object>> inputItems) {
        int depth = SUB_WORKFLOW_DEPTH.get();
        if (depth >= MAX_SUB_WORKFLOW_DEPTH) {
            throw new RuntimeException("Maximum sub-workflow depth (" + MAX_SUB_WORKFLOW_DEPTH
                + ") exceeded. Check for recursive workflow calls.");
        }
        SUB_WORKFLOW_DEPTH.set(depth + 1);

        try {
            WorkflowEntity workflow = workflowService.findById(workflowId);

            Map<String, Object> workflowSnapshot = new LinkedHashMap<>();
            workflowSnapshot.put("id", workflow.getId());
            workflowSnapshot.put("name", workflow.getName());
            workflowSnapshot.put("nodes", workflow.getNodes());
            workflowSnapshot.put("connections", workflow.getConnections());

            var execution = executionService.createExecution(
                    workflowId, workflowSnapshot, ExecutionMode.INTERNAL);

            executionService.updateStatus(execution.getId(), ExecutionStatus.RUNNING);

            WorkflowGraph graph = WorkflowGraph.parse(
                    workflow.getNodes(), workflow.getConnections(), objectMapper);

            WorkflowExecutionState state = new WorkflowExecutionState(
                    execution.getId(), workflowId, graph);
            runningExecutions.put(execution.getId(), state);

            try {
                List<String> order = graph.getTopologicalOrder();
                Map<String, String> variables = variableService.getAllVariablesAsMap();

                // Wrap the input items for the start node injection mechanism
                Map<String, Object> inputData = null;
                if (inputItems != null && !inputItems.isEmpty()) {
                    inputData = Map.of("_subWorkflowItems", (Object) inputItems);
                }

                for (String nodeId : order) {
                    if (state.isCancelled()) {
                        executionService.finish(execution.getId(), ExecutionStatus.CANCELED,
                                state.buildResultData(), "Sub-workflow cancelled");
                        return List.of();
                    }

                    boolean ok = executeNodeInWorkflow(nodeId, execution.getId(), workflow, inputData,
                            NodeExecutionContext.ExecutionMode.INTERNAL, graph, state, variables);
                    if (!ok) return List.of();

                    // Handle loop nodes
                    WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
                    if (graphNode != null && "loopOverItems".equals(graphNode.getType())) {
                        List<String> loopBody = graph.findLoopBodyNodes(nodeId);
                        if (!loopBody.isEmpty()) {
                            for (int iteration = 0; iteration < 10_000; iteration++) {
                                if (state.isCancelled()) break;
                                List<Map<String, Object>> loopOutput = state.getNodeOutput(nodeId, 1);
                                if (loopOutput.isEmpty()) break;
                                for (String bodyNodeId : loopBody) {
                                    if (state.isCancelled()) break;
                                    boolean bodyOk = executeNodeInWorkflow(bodyNodeId, execution.getId(),
                                            workflow, null, NodeExecutionContext.ExecutionMode.INTERNAL,
                                            graph, state, variables);
                                    if (!bodyOk) return List.of();
                                }
                                boolean loopOk = executeNodeInWorkflow(nodeId, execution.getId(),
                                        workflow, null, NodeExecutionContext.ExecutionMode.INTERNAL,
                                        graph, state, variables);
                                if (!loopOk) return List.of();
                            }
                        }
                    }
                }

                executionService.finish(execution.getId(), ExecutionStatus.SUCCESS,
                        state.buildResultData(), null);

                // Return last node's output
                for (int i = order.size() - 1; i >= 0; i--) {
                    List<List<Map<String, Object>>> outputs = state.getNodeOutputs().get(order.get(i));
                    if (outputs != null && !outputs.isEmpty() && !outputs.get(0).isEmpty()) {
                        return new ArrayList<>(outputs.get(0));
                    }
                }
                return List.of();

            } finally {
                runningExecutions.remove(execution.getId());
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Sub-workflow execution failed: " + e.getMessage(), e);
        } finally {
            SUB_WORKFLOW_DEPTH.set(depth);
        }
    }

    /**
     * Resume a workflow execution from a wait checkpoint. Called by WaitPollerService
     * (for time-based waits) and FormController (for form/webhook resumes).
     */
    @SuppressWarnings("unchecked")
    @Async("workflowExecutor")
    public void resumeFromWait(String executionId, String waitNodeId,
                                Map<String, Object> resumeData, Object checkpointState) {
        try {
            var executionResponse = executionService.getExecution(executionId);
            Object workflowData = executionResponse.getWorkflowData();
            String workflowId = executionResponse.getWorkflowId();

            Map<String, Object> wfMap = (Map<String, Object>) workflowData;
            WorkflowGraph graph = WorkflowGraph.parse(
                    wfMap.get("nodes"), wfMap.get("connections"), objectMapper);

            Map<String, Object> checkpoint = (Map<String, Object>) checkpointState;
            WorkflowExecutionState state = WorkflowExecutionState.fromCheckpoint(
                    executionId, workflowId, graph, checkpoint);

            runningExecutions.put(executionId, state);
            executionService.updateStatus(executionId, ExecutionStatus.RUNNING);

            // Inject resumeData as the wait node's output
            List<Map<String, Object>> resumeItems;
            if (resumeData != null && !resumeData.isEmpty()) {
                resumeItems = List.of(Map.of("json", (Object) resumeData));
            } else {
                resumeItems = List.of(Map.of("json", (Object) Map.of()));
            }
            state.storeOutput(waitNodeId, List.of(resumeItems));

            // Update wait node metadata to show it's completed
            WorkflowExecutionState.NodeExecutionMetadata waitMeta = state.getNodeMetadata().get(waitNodeId);
            if (waitMeta != null) {
                waitMeta.setStatus("success");
                waitMeta.setFinishedAt(Instant.now());
            }
            webSocketService.sendNodeFinished(executionId, waitNodeId,
                    waitMeta != null ? waitMeta.getNodeName() : waitNodeId,
                    state.getNodeOutputs().get(waitNodeId));

            // Continue execution from the node AFTER the wait node
            List<String> order = graph.getTopologicalOrder();
            int waitIndex = order.indexOf(waitNodeId);
            Map<String, String> variables = variableService.getAllVariablesAsMap();

            for (int i = waitIndex + 1; i < order.size(); i++) {
                String nodeId = order.get(i);

                if (state.isCancelled()) {
                    executionService.finish(executionId, ExecutionStatus.CANCELED,
                            state.buildResultData(), "Execution cancelled by user");
                    webSocketService.sendExecutionFinished(executionId, "CANCELED", state.buildResultData());
                    return;
                }

                // Use a null workflow entity — we use the graph from the snapshot
                boolean ok = executeNodeInResumedWorkflow(nodeId, executionId, workflowId,
                        graph, state, variables);
                if (!ok) return;

                // Handle loop nodes
                WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
                if (graphNode != null && "loopOverItems".equals(graphNode.getType())) {
                    List<String> loopBody = graph.findLoopBodyNodes(nodeId);
                    if (!loopBody.isEmpty()) {
                        for (int iteration = 0; iteration < 10_000; iteration++) {
                            if (state.isCancelled()) break;
                            List<Map<String, Object>> loopOutput = state.getNodeOutput(nodeId, 1);
                            if (loopOutput.isEmpty()) break;
                            for (String bodyNodeId : loopBody) {
                                if (state.isCancelled()) break;
                                boolean bodyOk = executeNodeInResumedWorkflow(bodyNodeId, executionId,
                                        workflowId, graph, state, variables);
                                if (!bodyOk) return;
                            }
                            boolean loopOk = executeNodeInResumedWorkflow(nodeId, executionId,
                                    workflowId, graph, state, variables);
                            if (!loopOk) return;
                        }
                    }
                }
            }

            executionService.finish(executionId, ExecutionStatus.SUCCESS,
                    state.buildResultData(), null);
            webSocketService.sendExecutionFinished(executionId, "SUCCESS", state.buildResultData());

            completeWebhookResponseWithLastOutput(executionId, state, order);

        } catch (Exception e) {
            log.error("Resume from wait failed: {}", executionId, e);
            executionService.finish(executionId, ExecutionStatus.ERROR, null, e.getMessage());
            webSocketService.sendExecutionFinished(executionId, "ERROR",
                    Map.of("error", e.getMessage()));
        } finally {
            runningExecutions.remove(executionId);
        }
    }

    /**
     * Execute a node during a resumed workflow. Similar to executeNodeInWorkflow but
     * works with a WorkflowGraph + workflowId instead of a WorkflowEntity.
     */
    private boolean executeNodeInResumedWorkflow(String nodeId, String executionId,
                                                  String workflowId, WorkflowGraph graph,
                                                  WorkflowExecutionState state,
                                                  Map<String, String> variables) {
        WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
        if (graphNode == null || graphNode.isDisabled()) return true;

        Optional<NodeRegistry.NodeRegistration> regOpt = nodeRegistry.getNode(
                graphNode.getType(), graphNode.getTypeVersion());
        if (regOpt.isEmpty()) {
            regOpt = nodeRegistry.getNode(graphNode.getType());
        }
        if (regOpt.isEmpty()) {
            log.warn("Node type not found: {}", graphNode.getType());
            return true;
        }

        NodeRegistry.NodeRegistration registration = regOpt.get();
        NodeInterface nodeInstance = registration.getNodeInstance();

        List<Map<String, Object>> nodeInput = state.collectInputForNode(nodeId);
        state.storeInput(nodeId, nodeInput);

        Map<String, Object> resolvedParams = resolveParameters(
                graphNode.getParameters(), nodeInput, state, variables, executionId);

        Map<String, Object> credentials = resolveCredentials(
                graphNode.getCredentials(), nodeInput, state, variables, executionId);

        // Collect AI inputs from sub-node connections
        Map<String, List<Object>> aiInputData = collectAllAiInputs(nodeId, graph, state);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(executionId)
                .workflowId(workflowId)
                .nodeId(nodeId)
                .nodeType(graphNode.getType())
                .nodeVersion(graphNode.getTypeVersion())
                .inputData(nodeInput)
                .parameters(resolvedParams)
                .credentials(credentials)
                .staticData(new HashMap<>())
                .workflowStaticData(state.getWorkflowStaticData())
                .nodeContextData(state.getOrCreateNodeContext(nodeId))
                .aiInputData(aiInputData)
                .executionMode(NodeExecutionContext.ExecutionMode.MANUAL)
                .continueOnFail(graphNode.isContinueOnFail())
                .build();

        WorkflowExecutionState.NodeExecutionMetadata meta = new WorkflowExecutionState.NodeExecutionMetadata();
        meta.setNodeId(nodeId);
        meta.setNodeName(graphNode.getName());
        meta.setStartedAt(Instant.now());
        meta.setStatus("running");
        meta.setExecutionOrder(state.nextExecutionOrder());
        state.getNodeMetadata().put(nodeId, meta);

        try {
            // AI sub-node: call supplyData() and store result, skip normal execute()
            if (nodeInstance instanceof AiSubNodeInterface aiSubNode && aiSubNode.shouldSupplyData(context)) {
                Object aiData = aiSubNode.supplyData(context);
                state.storeAiData(nodeId, aiData);
                state.storeOutput(nodeId, List.of(List.of()));
                meta.setFinishedAt(Instant.now());
                meta.setStatus("success");
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        state.getNodeOutputs().get(nodeId));
                return true;
            }

            nodeInstance.beforeExecute(context);
            NodeExecutionResult result = nodeInstance.execute(context);
            nodeInstance.afterExecute(context, result);

            // Handle nested waiting result during resume
            if (result.getWaitConfig() != null) {
                meta.setFinishedAt(Instant.now());
                meta.setStatus("waiting");
                state.storeOutput(nodeId, List.of(List.of()));

                Map<String, Object> checkpoint = state.toCheckpoint();
                NodeExecutionResult.WaitConfig wc = result.getWaitConfig();
                WaitType waitType = switch (wc.getWaitType()) {
                    case "form" -> WaitType.FORM;
                    case "webhook" -> WaitType.WEBHOOK;
                    case "timeInterval" -> WaitType.TIME_INTERVAL;
                    case "specificTime" -> WaitType.SPECIFIC_TIME;
                    default -> WaitType.WEBHOOK;
                };

                waitService.createWait(executionId, state.getWorkflowId(), nodeId,
                        waitType, wc.getResumeAt(), wc.getFormDefinition(), checkpoint);
                executionService.updateStatus(executionId, ExecutionStatus.WAITING);
                webSocketService.sendExecutionWaiting(executionId, nodeId, wc.getWaitType());

                log.info("Resumed execution {} re-waiting at node {} (type={})",
                        executionId, nodeId, wc.getWaitType());
                return false;
            }

            meta.setFinishedAt(Instant.now());

            if (result.getError() != null) {
                meta.setStatus("error");
                meta.setErrorMessage(result.getError().getMessage());

                if (!graphNode.isContinueOnFail()) {
                    state.storeOutput(nodeId, result.getOutput() != null ?
                            result.getOutput() : List.of(List.of()));
                    executionService.finish(executionId, ExecutionStatus.ERROR,
                            state.buildResultData(), result.getError().getMessage());
                    webSocketService.sendExecutionFinished(executionId, "ERROR", state.buildResultData());
                    return false;
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
                completeWebhookResponseIfPresent(executionId, state);
            }

            webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                    state.getNodeOutputs().get(nodeId));

        } catch (Exception e) {
            meta.setFinishedAt(Instant.now());
            meta.setStatus("error");
            meta.setErrorMessage(e.getMessage());

            log.error("Node execution failed during resume: {} ({})", graphNode.getName(), nodeId, e);

            if (graphNode.isContinueOnFail()) {
                List<Map<String, Object>> errorItems = List.of(
                        Map.of("json", Map.of("error", e.getMessage())));
                state.storeOutput(nodeId, List.of(errorItems));
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        state.getNodeOutputs().get(nodeId));
            } else {
                executionService.finish(executionId, ExecutionStatus.ERROR,
                        state.buildResultData(), e.getMessage());
                webSocketService.sendExecutionFinished(executionId, "ERROR", state.buildResultData());
                return false;
            }
        }

        return true;
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

            // When a specific trigger node started this execution (e.g. webhook),
            // only execute nodes reachable from that trigger — skip other triggers
            // and their exclusive downstream branches.
            String triggerNodeId = inputData != null ? (String) inputData.get("triggerNodeId") : null;
            if (triggerNodeId != null) {
                Set<String> reachable = graph.getReachableNodes(triggerNodeId);
                order = order.stream().filter(reachable::contains).toList();
            }

            for (String nodeId : order) {
                if (state.isCancelled()) {
                    executionService.finish(executionId, ExecutionStatus.CANCELED,
                            state.buildResultData(), "Execution cancelled by user");
                    webSocketService.sendExecutionFinished(executionId, "CANCELED", state.buildResultData());
                    return;
                }

                boolean ok = executeNodeInWorkflow(nodeId, executionId, workflow, inputData,
                        mode, graph, state, variables);
                if (!ok) return; // fatal error

                // Handle loop nodes: if this node is a loopOverItems and its loop output
                // (index 1) has data, re-execute the loop body iteratively.
                WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
                if (graphNode != null && "loopOverItems".equals(graphNode.getType())) {
                    List<String> loopBody = graph.findLoopBodyNodes(nodeId);
                    if (!loopBody.isEmpty()) {
                        int maxIterations = 10_000;
                        for (int iteration = 0; iteration < maxIterations; iteration++) {
                            if (state.isCancelled()) break;

                            // Check if loop output (index 1) has data
                            List<Map<String, Object>> loopOutput = state.getNodeOutput(nodeId, 1);
                            if (loopOutput.isEmpty()) break; // Loop complete

                            // Execute all loop body nodes
                            for (String bodyNodeId : loopBody) {
                                if (state.isCancelled()) break;
                                boolean bodyOk = executeNodeInWorkflow(bodyNodeId, executionId,
                                        workflow, null, mode, graph, state, variables);
                                if (!bodyOk) return;
                            }

                            // Re-execute the loop node itself (it reads new input from loop body)
                            boolean loopOk = executeNodeInWorkflow(nodeId, executionId,
                                    workflow, null, mode, graph, state, variables);
                            if (!loopOk) return;
                        }
                    }
                }
            }

            executionService.finish(executionId, ExecutionStatus.SUCCESS,
                    state.buildResultData(), null);
            webSocketService.sendExecutionFinished(executionId, "SUCCESS", state.buildResultData());

            // For lastNode mode: complete any pending webhook response with last node output
            completeWebhookResponseWithLastOutput(executionId, state, order);

        } catch (Exception e) {
            log.error("Workflow execution failed: {}", executionId, e);
            executionService.finish(executionId, ExecutionStatus.ERROR, null, e.getMessage());
            webSocketService.sendExecutionFinished(executionId, "ERROR",
                    Map.of("error", e.getMessage()));
            // Complete pending webhook response with error
            CompletableFuture<Map<String, Object>> future = pendingWebhookResponses.remove(executionId);
            if (future != null && !future.isDone()) {
                future.completeExceptionally(e);
            }
        } finally {
            runningExecutions.remove(executionId);
            // Safety net: complete any remaining future
            CompletableFuture<Map<String, Object>> leftover = pendingWebhookResponses.remove(executionId);
            if (leftover != null && !leftover.isDone()) {
                leftover.complete(Map.of("statusCode", 200, "body", Map.of("message", "Workflow completed")));
            }
        }
    }

    /**
     * Execute a single node within a workflow execution. Returns true if execution
     * should continue, false if a fatal error occurred and the workflow should stop.
     */
    private boolean executeNodeInWorkflow(String nodeId, String executionId,
                                           WorkflowEntity workflow, Map<String, Object> inputData,
                                           NodeExecutionContext.ExecutionMode mode,
                                           WorkflowGraph graph, WorkflowExecutionState state,
                                           Map<String, String> variables) {
        WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
        if (graphNode == null || graphNode.isDisabled()) return true;

        // Pin data bypass: use pinned output instead of executing the node
        Object pinDataObj = workflow.getPinData();
        if (pinDataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pinMap = (Map<String, Object>) pinDataObj;
            if (pinMap.containsKey(nodeId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pinnedItems = (List<Map<String, Object>>) pinMap.get(nodeId);
                List<List<Map<String, Object>>> output = List.of(pinnedItems);
                state.storeOutput(nodeId, output);
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        state.getNodeOutputs().get(nodeId));
                log.debug("Node {} skipped (pinned data: {} items)", nodeId, pinnedItems.size());
                return true;
            }
        }

        Optional<NodeRegistry.NodeRegistration> regOpt = nodeRegistry.getNode(
                graphNode.getType(), graphNode.getTypeVersion());
        if (regOpt.isEmpty()) {
            regOpt = nodeRegistry.getNode(graphNode.getType());
        }
        if (regOpt.isEmpty()) {
            log.warn("Node type not found: {}", graphNode.getType());
            return true;
        }

        NodeRegistry.NodeRegistration registration = regOpt.get();
        NodeInterface nodeInstance = registration.getNodeInstance();

        List<Map<String, Object>> nodeInput = state.collectInputForNode(nodeId);

        // Skip nodes that have incoming main connections but received no data.
        // This means the upstream branch (e.g. If true/false) did not route to this node.
        if (nodeInput.isEmpty()) {
            List<WorkflowGraph.Connection> mainIncoming = graph.getIncomingConnectionsByType(nodeId, "main");
            if (!mainIncoming.isEmpty()) {
                boolean hasUpstreamOutput = mainIncoming.stream().anyMatch(conn ->
                        !state.getNodeOutput(conn.getSourceNodeId(), conn.getSourceOutputIndex()).isEmpty());
                if (!hasUpstreamOutput) {
                    log.debug("Node {} skipped (no input data from upstream branch)", nodeId);
                    return true;
                }
            }
        }

        if (nodeInput.isEmpty() && inputData != null) {
            // Determine if this node should receive the initial input data.
            // For webhook executions, inject data into the specific trigger node.
            // For other modes, inject into the first node with no incoming connections.
            String triggerNodeId = (String) inputData.get("triggerNodeId");
            boolean isInputTarget = triggerNodeId != null
                    ? nodeId.equals(triggerNodeId)
                    : (graph.getStartNode() != null && graph.getStartNode().getId().equals(nodeId));

            if (isInputTarget) {
                if (inputData.containsKey("_subWorkflowItems")) {
                    // Sub-workflow execution: items are already in the correct format
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> subItems = (List<Map<String, Object>>) inputData.get("_subWorkflowItems");
                    nodeInput = subItems;
                } else if (inputData.containsKey("webhookData")) {
                    nodeInput = List.of(Map.of("json", inputData.get("webhookData")));
                } else {
                    nodeInput = List.of(Map.of("json", inputData));
                }
            }
        }

        state.storeInput(nodeId, nodeInput);

        Map<String, Object> resolvedParams = resolveParameters(
                graphNode.getParameters(), nodeInput, state, variables, executionId);

        Map<String, Object> credentials = resolveCredentials(
                graphNode.getCredentials(), nodeInput, state, variables, executionId);

        // Collect AI inputs from sub-node connections
        Map<String, List<Object>> aiInputData = collectAllAiInputs(nodeId, graph, state);

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
                .nodeContextData(state.getOrCreateNodeContext(nodeId))
                .aiInputData(aiInputData)
                .executionMode(mode)
                .continueOnFail(graphNode.isContinueOnFail())
                .build();

        WorkflowExecutionState.NodeExecutionMetadata meta = new WorkflowExecutionState.NodeExecutionMetadata();
        meta.setNodeId(nodeId);
        meta.setNodeName(graphNode.getName());
        meta.setStartedAt(Instant.now());
        meta.setStatus("running");
        meta.setExecutionOrder(state.nextExecutionOrder());
        state.getNodeMetadata().put(nodeId, meta);

        try {
            // AI sub-node: call supplyData() and store result, skip normal execute()
            if (nodeInstance instanceof AiSubNodeInterface aiSubNode && aiSubNode.shouldSupplyData(context)) {
                Object aiData = aiSubNode.supplyData(context);
                state.storeAiData(nodeId, aiData);
                state.storeOutput(nodeId, List.of(List.of()));
                meta.setFinishedAt(Instant.now());
                meta.setStatus("success");
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        state.getNodeOutputs().get(nodeId));
                return true;
            }

            nodeInstance.beforeExecute(context);
            NodeExecutionResult result = nodeInstance.execute(context);
            nodeInstance.afterExecute(context, result);

            // Handle waiting result — checkpoint state to DB and release thread
            if (result.getWaitConfig() != null) {
                meta.setFinishedAt(Instant.now());
                meta.setStatus("waiting");

                // Store empty output for the wait node so result data is consistent
                state.storeOutput(nodeId, List.of(List.of()));

                Map<String, Object> checkpoint = state.toCheckpoint();

                NodeExecutionResult.WaitConfig wc = result.getWaitConfig();
                WaitType waitType = switch (wc.getWaitType()) {
                    case "form" -> WaitType.FORM;
                    case "webhook" -> WaitType.WEBHOOK;
                    case "timeInterval" -> WaitType.TIME_INTERVAL;
                    case "specificTime" -> WaitType.SPECIFIC_TIME;
                    default -> WaitType.WEBHOOK;
                };

                waitService.createWait(executionId, state.getWorkflowId(), nodeId,
                        waitType, wc.getResumeAt(), wc.getFormDefinition(), checkpoint);

                executionService.updateStatus(executionId, ExecutionStatus.WAITING);
                webSocketService.sendExecutionWaiting(executionId, nodeId, wc.getWaitType());

                log.info("Execution {} waiting at node {} (type={})", executionId, nodeId, wc.getWaitType());
                return false; // stop the execution loop, release thread
            }

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
                    return false;
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
                completeWebhookResponseIfPresent(executionId, state);
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
                return false;
            }
        }

        return true;
    }

    public Map<String, Object> executeSingleNode(String nodeType, int typeVersion,
                                                  Map<String, Object> parameters,
                                                  Map<String, Object> credentialRefs,
                                                  List<Map<String, Object>> inputData,
                                                  String workflowId, String nodeId) {
        Optional<NodeRegistry.NodeRegistration> regOpt = nodeRegistry.getNode(nodeType, typeVersion);
        if (regOpt.isEmpty()) {
            regOpt = nodeRegistry.getNode(nodeType);
        }
        if (regOpt.isEmpty()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Node type not found: " + nodeType);
            err.put("output", List.of(List.of()));
            return err;
        }

        NodeRegistry.NodeRegistration registration = regOpt.get();
        NodeInterface nodeInstance = registration.getNodeInstance();

        if (inputData == null) inputData = List.of();

        Map<String, String> variables = variableService.getAllVariablesAsMap();
        Map<String, Object> resolvedParams = resolveParameters(
                parameters, inputData, null, variables, "single-node");
        Map<String, Object> credentials = resolveCredentials(
                credentialRefs, inputData, null, variables, "single-node");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId("single-node-" + System.currentTimeMillis())
                .workflowId(workflowId != null ? workflowId : "")
                .nodeId(nodeId != null ? nodeId : "")
                .nodeType(nodeType)
                .nodeVersion(typeVersion)
                .inputData(inputData)
                .parameters(resolvedParams)
                .credentials(credentials)
                .staticData(new HashMap<>())
                .workflowStaticData(new HashMap<>())
                .executionMode(NodeExecutionContext.ExecutionMode.MANUAL)
                .build();

        try {
            nodeInstance.beforeExecute(context);
            NodeExecutionResult result = nodeInstance.execute(context);
            nodeInstance.afterExecute(context, result);

            Map<String, Object> response = new LinkedHashMap<>();
            if (result.getError() != null) {
                response.put("output", result.getOutput() != null ? result.getOutput() : List.of(List.of()));
                response.put("error", result.getError().getMessage());
            } else {
                List<List<Map<String, Object>>> output = result.getOutput();
                if (output == null) output = List.of(List.of());
                response.put("output", output);
            }
            return response;
        } catch (Exception e) {
            log.error("Single node execution failed: {} ({})", nodeType, nodeId, e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            err.put("output", List.of(List.of()));
            return err;
        }
    }

    @SuppressWarnings("unchecked")
    private void completeWebhookResponseIfPresent(String executionId, WorkflowExecutionState state) {
        Object webhookResponse = state.getWorkflowStaticData().get("webhookResponse");
        if (webhookResponse instanceof Map) {
            CompletableFuture<Map<String, Object>> future = pendingWebhookResponses.remove(executionId);
            if (future != null && !future.isDone()) {
                future.complete((Map<String, Object>) webhookResponse);
            }
        }
    }

    private void completeWebhookResponseWithLastOutput(String executionId,
                                                        WorkflowExecutionState state,
                                                        List<String> order) {
        CompletableFuture<Map<String, Object>> future = pendingWebhookResponses.remove(executionId);
        if (future == null || future.isDone()) return;

        // Find the last node that produced output
        Object body = Map.of();
        for (int i = order.size() - 1; i >= 0; i--) {
            List<List<Map<String, Object>>> outputs = state.getNodeOutputs().get(order.get(i));
            if (outputs != null && !outputs.isEmpty() && !outputs.get(0).isEmpty()) {
                // Unwrap json from items for a cleaner response
                List<Object> items = new ArrayList<>();
                for (Map<String, Object> item : outputs.get(0)) {
                    items.add(item.getOrDefault("json", item));
                }
                body = items.size() == 1 ? items.get(0) : items;
                break;
            }
        }
        future.complete(Map.of("statusCode", 200,
                "headers", Map.of("Content-Type", "application/json"),
                "body", body));
    }

    private Map<String, List<Object>> collectAllAiInputs(String nodeId, WorkflowGraph graph,
                                                          WorkflowExecutionState state) {
        Map<String, List<Object>> aiInputs = new HashMap<>();
        List<WorkflowGraph.Connection> incoming = graph.getIncomingConnections()
                .getOrDefault(nodeId, List.of());

        for (WorkflowGraph.Connection conn : incoming) {
            if ("main".equals(conn.getType())) continue;
            Object aiData = state.getAiData(conn.getSourceNodeId());
            if (aiData != null) {
                aiInputs.computeIfAbsent(conn.getType(), k -> new ArrayList<>()).add(aiData);
            }
        }
        return aiInputs;
    }

    /**
     * Evaluate a single expression for preview in the expression editor.
     * Returns { "result": <value>, "error": "" } or { "result": null, "error": "<message>" }.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateExpressionPreview(String expression, List<Map<String, Object>> inputItems) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Map<String, Object> currentItemData = Map.of();
            if (inputItems != null && !inputItems.isEmpty()) {
                Map<String, Object> first = inputItems.get(0);
                Object json = first.get("json");
                if (json instanceof Map) {
                    currentItemData = (Map<String, Object>) json;
                }
            }

            Map<String, String> variables = variableService.getAllVariablesAsMap();

            ExpressionEvaluator.ExpressionContext ctx = ExpressionEvaluator.ExpressionContext.builder()
                    .currentItemData(currentItemData)
                    .inputItems(inputItems)
                    .variables(variables)
                    .executionId("preview")
                    .runIndex(0)
                    .build();

            Object evaluated = expressionEvaluator.resolveExpressions(expression, ctx);
            result.put("result", evaluated);
            result.put("error", "");
        } catch (Exception e) {
            result.put("result", null);
            result.put("error", e.getMessage());
        }
        return result;
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
    private Map<String, Object> resolveCredentials(Map<String, Object> credentialRefs,
                                                     List<Map<String, Object>> inputItems,
                                                     WorkflowExecutionState state,
                                                     Map<String, String> variables,
                                                     String executionId) {
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

        // Resolve any ={{...}} expressions in credential values
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

        Object result = expressionEvaluator.resolveExpressions(resolved, ctx);
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return resolved;
    }
}
