package io.trellis.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.entity.ExecutionEntity.ExecutionMode;
import io.trellis.entity.ExecutionEntity.ExecutionStatus;
import io.trellis.entity.WaitEntity.WaitType;
import io.trellis.entity.WorkflowEntity;
import io.trellis.entity.WorkflowVersionEntity;
import io.trellis.nodes.core.*;
import io.trellis.repository.WorkflowVersionRepository;
import io.trellis.service.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

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
    private final WorkflowVersionRepository workflowVersionRepository;

    @Resource(name = "branchExecutorService")
    private ExecutorService branchExecutorService;

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

    /**
     * Creates an execution record without starting it. The caller must
     * subsequently call {@link #triggerExecution(String)} to begin execution.
     */
    public String prepareExecution(String workflowId) {
        WorkflowEntity workflow = workflowService.findById(workflowId);

        Map<String, Object> workflowSnapshot = new LinkedHashMap<>();
        workflowSnapshot.put("id", workflow.getId());
        workflowSnapshot.put("name", workflow.getName());
        workflowSnapshot.put("nodes", workflow.getNodes());
        workflowSnapshot.put("connections", workflow.getConnections());

        var execution = executionService.createExecution(
                workflowId, workflowSnapshot, ExecutionMode.MANUAL);
        return execution.getId();
    }

    /**
     * Triggers async execution for a previously prepared execution.
     * @param triggerNodeId optional trigger node to start from (limits execution to reachable nodes)
     */
    public void triggerExecution(String executionId, String triggerNodeId) {
        var execution = executionService.getExecution(executionId);
        WorkflowEntity workflow = workflowService.findById(execution.getWorkflowId());
        Map<String, Object> inputData = null;
        if (triggerNodeId != null && !triggerNodeId.isBlank()) {
            inputData = Map.of("triggerNodeId", triggerNodeId);
        }
        executeAsync(executionId, workflow, inputData, NodeExecutionContext.ExecutionMode.MANUAL);
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
            state.loadStaticData(workflow.getStaticData());
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
                            NodeExecutionContext.ExecutionMode.INTERNAL, graph, state, variables, null);
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
                                            graph, state, variables, null);
                                    if (!bodyOk) return List.of();
                                }
                                boolean loopOk = executeNodeInWorkflow(nodeId, execution.getId(),
                                        workflow, null, NodeExecutionContext.ExecutionMode.INTERNAL,
                                        graph, state, variables, null);
                                if (!loopOk) return List.of();
                            }
                        }
                    }
                }

                executionService.finish(execution.getId(), ExecutionStatus.SUCCESS,
                        state.buildResultData(), null);
                saveStaticDataIfChanged(workflow, state);

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
                    "success", state.getNodeOutputs().get(waitNodeId),
                    waitMeta != null ? waitMeta.getDurationMs() : 0,
                    waitMeta != null ? waitMeta.getExecutionOrder() : 0);

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
            try {
                workflowService.updateStaticData(workflowId, state.getWorkflowStaticData());
            } catch (Exception ex) {
                log.warn("Failed to persist staticData for workflow {}: {}", workflowId, ex.getMessage());
            }
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
        webSocketService.sendNodeStarted(executionId, nodeId, graphNode.getName());

        try {
            // AI sub-node: call supplyData() and store result, skip normal execute()
            if (nodeInstance instanceof AiSubNodeInterface aiSubNode && aiSubNode.shouldSupplyData(context)) {
                Object aiData = aiSubNode.supplyData(context);
                state.storeAiData(nodeId, aiData);
                state.storeOutput(nodeId, List.of(List.of()));
                meta.setFinishedAt(Instant.now());
                meta.setStatus("success");
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        meta.getStatus(), state.getNodeOutputs().get(nodeId),
                        meta.getDurationMs(), meta.getExecutionOrder());
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
                    webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                            meta.getStatus(), state.getNodeOutputs().get(nodeId),
                            meta.getDurationMs(), meta.getExecutionOrder());
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
                    meta.getStatus(), state.getNodeOutputs().get(nodeId),
                    meta.getDurationMs(), meta.getExecutionOrder());

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
                        meta.getStatus(), state.getNodeOutputs().get(nodeId),
                        meta.getDurationMs(), meta.getExecutionOrder());
            } else {
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        meta.getStatus(), List.of(List.of()),
                        meta.getDurationMs(), meta.getExecutionOrder());
                executionService.finish(executionId, ExecutionStatus.ERROR,
                        state.buildResultData(), e.getMessage());
                webSocketService.sendExecutionFinished(executionId, "ERROR", state.buildResultData());
                return false;
            }
        }

        return true;
    }

    /**
     * Resolve the effective pin data for this execution based on mode.
     * MANUAL executions use the draft pin data from the workflow entity.
     * Production executions (WEBHOOK, TRIGGER, POLLING) use pin data from
     * the latest published version (only if the user chose to publish with pin data).
     */
    private Object resolveEffectivePinData(WorkflowEntity workflow, NodeExecutionContext.ExecutionMode mode) {
        if (mode == NodeExecutionContext.ExecutionMode.MANUAL) {
            return workflow.getPinData();
        }
        // For production modes, use the published version's pin data (if any)
        return workflowVersionRepository.findFirstByWorkflowIdOrderByVersionNumberDesc(workflow.getId())
                .map(WorkflowVersionEntity::getPinData)
                .orElse(null);
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
            state.loadStaticData(workflow.getStaticData());
            runningExecutions.put(executionId, state);

            webSocketService.sendExecutionStarted(executionId, workflow.getId());

            Object effectivePinData = resolveEffectivePinData(workflow, mode);

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

            boolean parallelEnabled = isParallelEnabled(workflow);

            if (parallelEnabled) {
                log.info("Parallel branch execution enabled for workflow {}", workflow.getId());
                executeParallel(executionId, workflow, inputData, mode, graph, state,
                        variables, effectivePinData, order);
                if (state.isCancelled() || state.getExecutionFinished().get()) return;
            } else {
                for (String nodeId : order) {
                    if (state.isCancelled()) {
                        executionService.finish(executionId, ExecutionStatus.CANCELED,
                                state.buildResultData(), "Execution cancelled by user");
                        webSocketService.sendExecutionFinished(executionId, "CANCELED", state.buildResultData());
                        return;
                    }

                    boolean ok = executeSingleNodeInBatch(nodeId, executionId, workflow, inputData,
                            mode, graph, state, variables, effectivePinData);
                    if (!ok) return;
                }
            }

            executionService.finish(executionId, ExecutionStatus.SUCCESS,
                    state.buildResultData(), null);
            saveStaticDataIfChanged(workflow, state);
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
                                           Map<String, String> variables, Object effectivePinData) {
        WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
        if (graphNode == null || graphNode.isDisabled()) return true;

        // Pin data bypass: use pinned output instead of executing the node
        Object pinDataObj = effectivePinData;
        if (pinDataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pinMap = (Map<String, Object>) pinDataObj;
            if (pinMap.containsKey(nodeId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> pinnedItems = (List<Map<String, Object>>) pinMap.get(nodeId);
                List<List<Map<String, Object>>> output = List.of(pinnedItems);
                state.storeOutput(nodeId, output);
                webSocketService.sendNodeStarted(executionId, nodeId, graphNode.getName());
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        "success", state.getNodeOutputs().get(nodeId), 0, 0);
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
        webSocketService.sendNodeStarted(executionId, nodeId, graphNode.getName());

        try {
            // AI sub-node: call supplyData() and store result, skip normal execute()
            if (nodeInstance instanceof AiSubNodeInterface aiSubNode && aiSubNode.shouldSupplyData(context)) {
                Object aiData = aiSubNode.supplyData(context);
                state.storeAiData(nodeId, aiData);
                state.storeOutput(nodeId, List.of(List.of()));
                meta.setFinishedAt(Instant.now());
                meta.setStatus("success");
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        meta.getStatus(), state.getNodeOutputs().get(nodeId),
                        meta.getDurationMs(), meta.getExecutionOrder());
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
                    webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                            meta.getStatus(), state.getNodeOutputs().get(nodeId),
                            meta.getDurationMs(), meta.getExecutionOrder());
                    if (state.tryMarkFinished()) {
                        executionService.finish(executionId, ExecutionStatus.ERROR,
                                state.buildResultData(), result.getError().getMessage());
                        webSocketService.sendExecutionFinished(
                                executionId, "ERROR", state.buildResultData());
                    }
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
                    meta.getStatus(), state.getNodeOutputs().get(nodeId),
                    meta.getDurationMs(), meta.getExecutionOrder());

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
                        meta.getStatus(), state.getNodeOutputs().get(nodeId),
                        meta.getDurationMs(), meta.getExecutionOrder());
            } else {
                webSocketService.sendNodeFinished(executionId, nodeId, graphNode.getName(),
                        meta.getStatus(), List.of(List.of()),
                        meta.getDurationMs(), meta.getExecutionOrder());
                if (state.tryMarkFinished()) {
                    executionService.finish(executionId, ExecutionStatus.ERROR,
                            state.buildResultData(), e.getMessage());
                    webSocketService.sendExecutionFinished(
                            executionId, "ERROR", state.buildResultData());
                }
                return false;
            }
        }

        return true;
    }

    /**
     * Check if the workflow has parallel branch execution enabled in its settings.
     */
    @SuppressWarnings("unchecked")
    private boolean isParallelEnabled(WorkflowEntity workflow) {
        Object settingsObj = workflow.getSettings();
        if (settingsObj instanceof Map) {
            Map<String, Object> settings = (Map<String, Object>) settingsObj;
            return Boolean.TRUE.equals(settings.get("parallelExecution"));
        }
        return false;
    }

    /**
     * Execute the loop body for a loopOverItems node. Extracted to be shared by
     * both the sequential and parallel execution paths.
     * Returns true if execution should continue, false on fatal error.
     */
    private boolean handleLoopBody(String loopNodeId, String executionId,
                                    WorkflowEntity workflow, NodeExecutionContext.ExecutionMode mode,
                                    WorkflowGraph graph, WorkflowExecutionState state,
                                    Map<String, String> variables, Object effectivePinData) {
        List<String> loopBody = graph.findLoopBodyNodes(loopNodeId);
        if (loopBody.isEmpty()) return true;

        int maxIterations = 10_000;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            if (state.isCancelled()) break;

            List<Map<String, Object>> loopOutput = state.getNodeOutput(loopNodeId, 1);
            if (loopOutput.isEmpty()) break;

            for (String bodyNodeId : loopBody) {
                if (state.isCancelled()) break;
                boolean bodyOk = executeNodeInWorkflow(bodyNodeId, executionId,
                        workflow, null, mode, graph, state, variables, effectivePinData);
                if (!bodyOk) return false;
            }

            boolean loopOk = executeNodeInWorkflow(loopNodeId, executionId,
                    workflow, null, mode, graph, state, variables, effectivePinData);
            if (!loopOk) return false;
        }
        return true;
    }

    /**
     * Execute the workflow using the ready-set parallel algorithm.
     * Nodes whose predecessors have all completed become "ready" and are
     * submitted to a thread pool simultaneously. Single-ready-node batches
     * execute on the current thread to avoid overhead.
     */
    private void executeParallel(String executionId, WorkflowEntity workflow,
                                  Map<String, Object> inputData,
                                  NodeExecutionContext.ExecutionMode mode,
                                  WorkflowGraph graph, WorkflowExecutionState state,
                                  Map<String, String> variables, Object effectivePinData,
                                  List<String> order) {
        Set<String> loopBodyNodes = graph.findAllLoopBodyNodes();
        Set<String> completed = ConcurrentHashMap.newKeySet();
        Set<String> scheduled = new HashSet<>(order);  // only nodes in the execution order

        // Filter: remove loop body nodes from the scheduling set (they run inline)
        scheduled.removeAll(loopBodyNodes);

        while (true) {
            if (state.isCancelled()) {
                if (state.tryMarkFinished()) {
                    executionService.finish(executionId, ExecutionStatus.CANCELED,
                            state.buildResultData(), "Execution cancelled by user");
                    webSocketService.sendExecutionFinished(executionId, "CANCELED", state.buildResultData());
                }
                return;
            }

            // Find all ready nodes: in scheduled set, not yet completed, all predecessors completed
            List<String> ready = new ArrayList<>();
            for (String nodeId : order) {
                if (!scheduled.contains(nodeId) || completed.contains(nodeId)) continue;
                Set<String> preds = graph.getPredecessors(nodeId);
                // Predecessors that are loop body nodes are considered satisfied if
                // their parent loop node has completed
                preds.removeAll(loopBodyNodes);
                if (completed.containsAll(preds)) {
                    ready.add(nodeId);
                }
            }

            if (ready.isEmpty()) break;  // all done

            // Execute the batch
            boolean ok = executeNodeBatchParallel(ready, executionId, workflow, inputData,
                    mode, graph, state, variables, effectivePinData, completed);
            if (!ok) return;  // fatal error already handled
        }
    }

    /**
     * Execute a batch of ready nodes. If only one node, runs on the current thread.
     * If multiple nodes, submits them to the branch executor in parallel.
     * Returns true if execution should continue, false on fatal error.
     */
    private boolean executeNodeBatchParallel(List<String> readyNodes, String executionId,
                                              WorkflowEntity workflow, Map<String, Object> inputData,
                                              NodeExecutionContext.ExecutionMode mode,
                                              WorkflowGraph graph, WorkflowExecutionState state,
                                              Map<String, String> variables, Object effectivePinData,
                                              Set<String> completed) {
        if (readyNodes.size() == 1) {
            // Single node — execute on current thread to avoid overhead
            boolean ok = executeSingleNodeInBatch(readyNodes.get(0), executionId, workflow,
                    inputData, mode, graph, state, variables, effectivePinData);
            if (ok) {
                completed.add(readyNodes.get(0));
            }
            return ok;
        }

        // Multiple ready nodes — submit all to thread pool
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String nodeId : readyNodes) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                    executeSingleNodeInBatch(nodeId, executionId, workflow,
                            inputData, mode, graph, state, variables, effectivePinData),
                    branchExecutorService);
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Check results
        for (int i = 0; i < futures.size(); i++) {
            boolean ok = futures.get(i).join();
            if (ok) {
                completed.add(readyNodes.get(i));
            } else {
                return false;  // fatal error — workflow should stop
            }
        }
        return true;
    }

    /**
     * Execute a single node and its loop body (if it's a loopOverItems node).
     * Used as the unit of work for both sequential and parallel batch execution.
     */
    private boolean executeSingleNodeInBatch(String nodeId, String executionId,
                                              WorkflowEntity workflow, Map<String, Object> inputData,
                                              NodeExecutionContext.ExecutionMode mode,
                                              WorkflowGraph graph, WorkflowExecutionState state,
                                              Map<String, String> variables, Object effectivePinData) {
        boolean ok = executeNodeInWorkflow(nodeId, executionId, workflow, inputData,
                mode, graph, state, variables, effectivePinData);
        if (!ok) return false;

        // Handle loop nodes inline
        WorkflowGraph.WorkflowNode graphNode = graph.getNodes().get(nodeId);
        if (graphNode != null && "loopOverItems".equals(graphNode.getType())) {
            return handleLoopBody(nodeId, executionId, workflow, mode,
                    graph, state, variables, effectivePinData);
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

    /**
     * Persist workflow staticData back to the entity if any nodes produced static data.
     * Uses a fresh DB read + merge to avoid overwriting changes made by concurrent executions
     * (e.g. when multiple app instances run the same workflow).
     */
    @SuppressWarnings("unchecked")
    private void saveStaticDataIfChanged(WorkflowEntity workflow, WorkflowExecutionState state) {
        Map<String, Object> stateData = state.getWorkflowStaticData();
        if (stateData.isEmpty()) return;

        try {
            // Re-read the entity to get the latest staticData (another execution may have updated it)
            WorkflowEntity fresh = workflowService.findById(workflow.getId());
            Map<String, Object> existing = new LinkedHashMap<>();
            if (fresh.getStaticData() instanceof Map) {
                existing.putAll((Map<String, Object>) fresh.getStaticData());
            }
            existing.putAll(stateData);
            workflowService.updateStaticData(workflow.getId(), existing);
        } catch (Exception e) {
            log.warn("Failed to persist staticData for workflow {}: {}", workflow.getId(), e.getMessage());
        }
    }
}
