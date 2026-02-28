package io.trellis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.dto.*;
import io.trellis.nodes.core.NodeRegistry;
import io.trellis.nodes.core.NodeRegistry.NodeRegistration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpSystemToolService {

    private final NodeRegistry nodeRegistry;
    private final WorkflowService workflowService;
    private final ExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final RemoteControlService remoteControlService;
    private final WebSocketService webSocketService;
    private final BrowserSessionRegistry browserSessionRegistry;

    private static final String TOOL_PREFIX = "trellis_";

    // --- Tool Definitions ---

    public List<Map<String, Object>> getSystemToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(toolDef("trellis_list_node_types",
                "List available node types in the Trellis workflow engine. Returns type, displayName, category, description, and flags for each node.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "category", prop("string", "Filter by category name (case-insensitive)"),
                                "search", prop("string", "Search term to filter by type or displayName")
                        )
                )));

        tools.add(toolDef("trellis_get_node_type",
                "Get full details of a specific node type including all parameters, inputs, and outputs. Use this to understand how to configure a node.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "type", prop("string", "The node type identifier (e.g. 'httpRequest', 'code', 'ifNode')")
                        ),
                        "required", List.of("type")
                )));

        tools.add(toolDef("trellis_list_workflows",
                "List all workflows with summary info (id, name, description, published status, tags).",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "projectId", prop("string", "Filter workflows by project ID")
                        )
                )));

        tools.add(toolDef("trellis_get_workflow",
                "Get the full workflow definition including nodes, connections, and settings.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The workflow ID")
                        ),
                        "required", List.of("id")
                )));

        tools.add(toolDef("trellis_create_workflow",
                "Create a new workflow. Provide a name and optionally nodes and connections. Returns the created workflow with its ID.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "name", prop("string", "Workflow name (required)"),
                                "description", prop("string", "Workflow description"),
                                "projectId", prop("string", "Project ID to associate the workflow with"),
                                "nodes", Map.of("type", "array", "description", "Array of workflow node objects"),
                                "connections", Map.of("type", "object", "description", "Connection map between nodes")
                        ),
                        "required", List.of("name")
                )));

        tools.add(toolDef("trellis_update_workflow",
                "Update an existing workflow. Only provided fields are updated.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The workflow ID (required)"),
                                "name", prop("string", "New workflow name"),
                                "description", prop("string", "New description"),
                                "nodes", Map.of("type", "array", "description", "Updated node array"),
                                "connections", Map.of("type", "object", "description", "Updated connections map")
                        ),
                        "required", List.of("id")
                )));

        tools.add(toolDef("trellis_list_executions",
                "List workflow executions with optional filtering. Returns id, workflowId, status, mode, startedAt, finishedAt.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "workflowId", prop("string", "Filter by workflow ID"),
                                "status", prop("string", "Filter by status (NEW, RUNNING, SUCCESS, ERROR, CANCELED, WAITING)"),
                                "limit", Map.of("type", "integer", "description", "Max results to return (default 20, max 100)")
                        )
                )));

        tools.add(toolDef("trellis_get_execution",
                "Get execution details including result data and node outputs.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The execution ID")
                        ),
                        "required", List.of("id")
                )));

        tools.add(toolDef("trellis_workflow_guide",
                "Returns instructional documentation on how to build Trellis workflows, including node structure, connections format, expression syntax, and common patterns.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "topic", prop("string",
                                        "Topic to get help on: 'overview', 'node_wiring', 'parameters', 'common_patterns', or 'all' (default: 'all')")
                        )
                )));

        tools.add(toolDef("trellis_list_browser_sessions",
                "List active browser sessions connected to Trellis. Returns session IDs that can be used to target specific browser tabs with canvas pushes or control requests.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap()
                )));

        tools.add(toolDef("trellis_browser_control",
                "Request control of the user's Trellis browser tab. Can navigate to a URL within Trellis or load a workflow definition for review. Requires user consent via an in-browser approval dialog.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "action", prop("string", "Action to perform: 'navigate' or 'load_workflow'"),
                                "targetUrl", prop("string", "Target URL path for navigate action (e.g. '/workflow/abc123')"),
                                "workflowData", Map.of("type", "object", "description",
                                        "Workflow data for load_workflow action (should include name, nodes, connections)"),
                                "message", prop("string", "Human-readable message explaining what the agent wants to do and why"),
                                "browserSessionId", prop("string", "Target browser session ID. If omitted, auto-targets when exactly one session is active.")
                        ),
                        "required", List.of("action", "message")
                )));

        tools.add(toolDef("trellis_push_to_canvas",
                "Push a workflow JSON definition directly to the user's currently open workflow editor canvas. The workflow data (nodes, connections, name, description) will be loaded into whichever workflow editor tab the user has open. Does not require consent — the data is loaded as unsaved changes that the user can review and save manually.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "name", prop("string", "Workflow name (optional, updates the current workflow name if provided)"),
                                "description", prop("string", "Workflow description (optional)"),
                                "nodes", Map.of("type", "array", "description", "Array of workflow node objects to load onto the canvas"),
                                "connections", Map.of("type", "object", "description", "Connection map between nodes"),
                                "message", prop("string", "Human-readable message explaining what is being pushed to the canvas"),
                                "browserSessionId", prop("string", "Target browser session ID. If omitted, auto-targets when exactly one session is active.")
                        ),
                        "required", List.of("nodes", "connections")
                )));

        tools.add(toolDef("trellis_publish_workflow",
                "Publish the current draft of a workflow as a new immutable version. This creates a versioned snapshot without modifying the published workflow's existing versions. Use this instead of update_workflow when the workflow is already published.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The workflow ID to publish"),
                                "versionName", prop("string", "Optional name for this version (e.g. 'v2 - added error handling'). Auto-generated if omitted."),
                                "description", prop("string", "Optional description of what changed in this version")
                        ),
                        "required", List.of("id")
                )));

        return tools;
    }

    public boolean isSystemTool(String name) {
        return name != null && name.startsWith(TOOL_PREFIX);
    }

    // --- Tool Dispatch ---

    private static final Set<String> CONSENT_REQUIRED_TOOLS = Set.of(
            "trellis_browser_control",
            "trellis_push_to_canvas",
            "trellis_create_workflow",
            "trellis_update_workflow",
            "trellis_publish_workflow"
    );

    private boolean requiresConsent(String toolName) {
        return CONSENT_REQUIRED_TOOLS.contains(toolName);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleToolCall(String name, Map<String, Object> arguments) {
        if (arguments == null) arguments = Map.of();

        try {
            String targetSession = null;

            if (requiresConsent(name)) {
                // Resolve browser session, generate description, request consent
                targetSession = resolveTargetSession((String) arguments.get("browserSessionId"));
                String description = generateToolDescription(name, arguments);

                Map<String, Object> visibleArgs = new LinkedHashMap<>(arguments);
                visibleArgs.remove("browserSessionId");

                boolean approved = remoteControlService.requestToolConsent(targetSession, name, description, visibleArgs);
                if (!approved) {
                    return Map.of("content", List.of(Map.of("type", "text", "text",
                            "User denied the request to execute " + name + ".")));
                }
            }

            // Dispatch to handler
            Object result = switch (name) {
                case "trellis_list_node_types" -> handleListNodeTypes(arguments);
                case "trellis_get_node_type" -> handleGetNodeType(arguments);
                case "trellis_list_workflows" -> handleListWorkflows(arguments);
                case "trellis_get_workflow" -> handleGetWorkflow(arguments);
                case "trellis_create_workflow" -> handleCreateWorkflow(arguments);
                case "trellis_update_workflow" -> handleUpdateWorkflow(arguments);
                case "trellis_list_executions" -> handleListExecutions(arguments);
                case "trellis_get_execution" -> handleGetExecution(arguments);
                case "trellis_workflow_guide" -> handleWorkflowGuide(arguments);
                case "trellis_list_browser_sessions" -> handleListBrowserSessions(arguments);
                case "trellis_browser_control" -> handleBrowserControl(arguments, targetSession);
                case "trellis_push_to_canvas" -> handlePushToCanvas(arguments, targetSession);
                case "trellis_publish_workflow" -> handlePublishWorkflow(arguments);
                default -> throw new IllegalArgumentException("Unknown system tool: " + name);
            };

            String text = objectMapper.writeValueAsString(result);
            return Map.of("content", List.of(Map.of("type", "text", "text", text)));
        } catch (Exception e) {
            log.error("Error handling system tool call: {}", name, e);
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Error: " + e.getMessage())),
                    "isError", true);
        }
    }

    private String generateToolDescription(String name, Map<String, Object> args) {
        return switch (name) {
            case "trellis_list_node_types" -> {
                String category = (String) args.get("category");
                String search = (String) args.get("search");
                if (category != null && !category.isBlank()) {
                    yield "List node types in category '" + category + "'";
                } else if (search != null && !search.isBlank()) {
                    yield "Search node types for '" + search + "'";
                } else {
                    yield "List all available node types";
                }
            }
            case "trellis_get_node_type" -> "Get details for node type '" + args.getOrDefault("type", "?") + "'";
            case "trellis_list_workflows" -> {
                String projectId = (String) args.get("projectId");
                yield projectId != null ? "List workflows in project " + projectId : "List all workflows";
            }
            case "trellis_get_workflow" -> "Get workflow definition for '" + args.getOrDefault("id", "?") + "'";
            case "trellis_create_workflow" -> "Create a new workflow named '" + args.getOrDefault("name", "?") + "'";
            case "trellis_update_workflow" -> "Update workflow '" + args.getOrDefault("id", "?") + "'";
            case "trellis_list_executions" -> {
                String wfId = (String) args.get("workflowId");
                yield wfId != null ? "List executions for workflow " + wfId : "List recent workflow executions";
            }
            case "trellis_get_execution" -> "Get execution details for '" + args.getOrDefault("id", "?") + "'";
            case "trellis_workflow_guide" -> "Get workflow building guide" +
                    (args.get("topic") != null ? " (topic: " + args.get("topic") + ")" : "");
            case "trellis_list_browser_sessions" -> "List active browser sessions";
            case "trellis_browser_control" -> {
                String action = (String) args.getOrDefault("action", "?");
                String message = (String) args.get("message");
                if ("navigate".equals(action)) {
                    yield "Navigate browser to " + args.getOrDefault("targetUrl", "?");
                } else if ("load_workflow".equals(action)) {
                    yield "Load workflow definition into editor";
                } else {
                    yield message != null ? message : "Control browser: " + action;
                }
            }
            case "trellis_push_to_canvas" -> {
                String wfName = (String) args.get("name");
                yield wfName != null
                        ? "Push workflow '" + wfName + "' to the editor canvas"
                        : "Push workflow data to the editor canvas";
            }
            case "trellis_publish_workflow" -> {
                String vName = (String) args.get("versionName");
                yield vName != null
                        ? "Publish workflow '" + args.getOrDefault("id", "?") + "' as '" + vName + "'"
                        : "Publish workflow '" + args.getOrDefault("id", "?") + "' as a new version";
            }
            default -> "Execute " + name;
        };
    }

    // --- Tool Handlers ---

    private Object handleListNodeTypes(Map<String, Object> args) {
        String category = (String) args.get("category");
        String search = (String) args.get("search");

        Collection<NodeRegistration> nodes;
        if (category != null && !category.isBlank()) {
            nodes = nodeRegistry.getNodesByCategory(category);
        } else {
            nodes = nodeRegistry.getAllNodes();
        }

        List<Map<String, Object>> result = nodes.stream()
                .filter(n -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase();
                    return n.getType().toLowerCase().contains(s)
                            || n.getDisplayName().toLowerCase().contains(s)
                            || (n.getDescription() != null && n.getDescription().toLowerCase().contains(s));
                })
                .sorted(Comparator.comparing(NodeRegistration::getCategory)
                        .thenComparing(NodeRegistration::getDisplayName))
                .map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", n.getType());
                    m.put("displayName", n.getDisplayName());
                    m.put("category", n.getCategory());
                    m.put("description", n.getDescription());
                    m.put("version", n.getVersion());
                    m.put("isTrigger", n.isTrigger());
                    m.put("isPolling", n.isPolling());
                    if (!n.getCredentials().isEmpty()) {
                        m.put("credentials", n.getCredentials());
                    }
                    return m;
                })
                .toList();

        return Map.of("nodeTypes", result, "count", result.size());
    }

    private Object handleGetNodeType(Map<String, Object> args) {
        String type = (String) args.get("type");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("'type' is required");
        }

        NodeRegistration node = nodeRegistry.getNode(type)
                .orElseThrow(() -> new IllegalArgumentException("Node type not found: " + type));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", node.getType());
        result.put("displayName", node.getDisplayName());
        result.put("description", node.getDescription());
        result.put("category", node.getCategory());
        result.put("version", node.getVersion());
        result.put("icon", node.getIcon());
        result.put("isTrigger", node.isTrigger());
        result.put("isPolling", node.isPolling());
        result.put("credentials", node.getCredentials());
        result.put("group", node.getGroup());
        result.put("subtitle", node.getSubtitle());
        result.put("documentationUrl", node.getDocumentationUrl());

        // Serialize parameters, inputs, outputs via ObjectMapper for clean JSON
        result.put("parameters", objectMapper.convertValue(node.getParameters(), List.class));
        result.put("inputs", objectMapper.convertValue(node.getInputs(), List.class));
        result.put("outputs", objectMapper.convertValue(node.getOutputs(), List.class));

        List<Integer> versions = nodeRegistry.getVersions(type);
        if (versions.size() > 1) {
            result.put("availableVersions", versions);
        }

        return result;
    }

    private Object handleListWorkflows(Map<String, Object> args) {
        String projectId = (String) args.get("projectId");

        List<WorkflowResponse> workflows;
        if (projectId != null && !projectId.isBlank()) {
            workflows = workflowService.listWorkflowsByProject(projectId);
        } else {
            workflows = workflowService.listWorkflows();
        }

        List<Map<String, Object>> result = workflows.stream()
                .map(wf -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", wf.getId());
                    m.put("name", wf.getName());
                    m.put("description", wf.getDescription());
                    m.put("published", wf.isPublished());
                    m.put("currentVersion", wf.getCurrentVersion());
                    m.put("mcpEnabled", wf.isMcpEnabled());
                    m.put("swaggerEnabled", wf.isSwaggerEnabled());
                    if (wf.getTags() != null && !wf.getTags().isEmpty()) {
                        m.put("tags", wf.getTags().stream()
                                .map(TagResponse::getName)
                                .toList());
                    }
                    m.put("createdAt", wf.getCreatedAt());
                    m.put("updatedAt", wf.getUpdatedAt());
                    return m;
                })
                .toList();

        return Map.of("workflows", result, "count", result.size());
    }

    private Object handleGetWorkflow(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("'id' is required");
        }

        WorkflowResponse wf = workflowService.getWorkflow(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("description", wf.getDescription());
        result.put("projectId", wf.getProjectId());
        result.put("published", wf.isPublished());
        result.put("currentVersion", wf.getCurrentVersion());
        result.put("nodes", wf.getNodes());
        result.put("connections", wf.getConnections());
        result.put("settings", wf.getSettings());
        result.put("mcpEnabled", wf.isMcpEnabled());
        result.put("mcpDescription", wf.getMcpDescription());
        result.put("swaggerEnabled", wf.isSwaggerEnabled());
        result.put("createdAt", wf.getCreatedAt());
        result.put("updatedAt", wf.getUpdatedAt());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object handleCreateWorkflow(Map<String, Object> args) {
        String name = (String) args.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("'name' is required");
        }

        WorkflowCreateRequest request = new WorkflowCreateRequest();
        request.setName(name);
        request.setDescription((String) args.get("description"));
        request.setProjectId((String) args.get("projectId"));
        request.setNodes(args.get("nodes"));
        request.setConnections(args.get("connections"));

        WorkflowResponse wf = workflowService.createWorkflow(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("description", wf.getDescription());
        result.put("message", "Workflow created successfully");
        result.put("editorUrl", "/workflow/" + wf.getId());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object handleUpdateWorkflow(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("'id' is required");
        }

        // Block updates to published workflows
        WorkflowResponse existing = workflowService.getWorkflow(id);
        if (existing.isPublished()) {
            throw new IllegalArgumentException(
                    "Workflow '" + id + "' is published and cannot be modified directly. "
                    + "Use trellis_publish_workflow to create a new version instead.");
        }

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        if (args.containsKey("name")) request.setName((String) args.get("name"));
        if (args.containsKey("description")) request.setDescription((String) args.get("description"));
        if (args.containsKey("nodes")) request.setNodes(args.get("nodes"));
        if (args.containsKey("connections")) request.setConnections(args.get("connections"));

        WorkflowResponse wf = workflowService.updateWorkflow(id, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("message", "Workflow updated successfully");
        result.put("editorUrl", "/workflow/" + wf.getId());
        return result;
    }

    private Object handlePublishWorkflow(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("'id' is required");
        }

        PublishWorkflowRequest request = new PublishWorkflowRequest();
        if (args.containsKey("versionName")) request.setVersionName((String) args.get("versionName"));
        if (args.containsKey("description")) request.setDescription((String) args.get("description"));

        WorkflowResponse wf = workflowService.publishWorkflow(id, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("currentVersion", wf.getCurrentVersion());
        result.put("published", wf.isPublished());
        result.put("message", "Workflow published as version " + wf.getCurrentVersion());
        result.put("editorUrl", "/workflow/" + wf.getId());
        return result;
    }

    private Object handleListExecutions(Map<String, Object> args) {
        String workflowId = (String) args.get("workflowId");
        String status = (String) args.get("status");
        int limit = 20;
        if (args.get("limit") instanceof Number n) {
            limit = Math.min(Math.max(n.intValue(), 1), 100);
        }

        Page<ExecutionListResponse> page = executionService.listExecutions(
                workflowId, status, 0, limit);

        List<Map<String, Object>> result = page.getContent().stream()
                .map(ex -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", ex.getId());
                    m.put("workflowId", ex.getWorkflowId());
                    m.put("status", ex.getStatus());
                    m.put("mode", ex.getMode());
                    m.put("startedAt", ex.getStartedAt());
                    m.put("finishedAt", ex.getFinishedAt());
                    if (ex.getErrorMessage() != null) {
                        m.put("errorMessage", ex.getErrorMessage());
                    }
                    return m;
                })
                .toList();

        return Map.of("executions", result, "count", result.size(), "totalCount", page.getTotalElements());
    }

    private Object handleGetExecution(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("'id' is required");
        }

        ExecutionResponse ex = executionService.getExecution(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ex.getId());
        result.put("workflowId", ex.getWorkflowId());
        result.put("status", ex.getStatus());
        result.put("mode", ex.getMode());
        result.put("startedAt", ex.getStartedAt());
        result.put("finishedAt", ex.getFinishedAt());
        result.put("errorMessage", ex.getErrorMessage());
        result.put("resultData", ex.getResultData());
        return result;
    }

    private Object handleWorkflowGuide(Map<String, Object> args) {
        String topic = (String) args.get("topic");
        if (topic == null || topic.isBlank()) topic = "all";

        Map<String, Object> guide = new LinkedHashMap<>();

        if ("all".equals(topic) || "overview".equals(topic)) {
            guide.put("overview", GUIDE_OVERVIEW);
        }
        if ("all".equals(topic) || "node_wiring".equals(topic)) {
            guide.put("node_wiring", GUIDE_NODE_WIRING);
        }
        if ("all".equals(topic) || "parameters".equals(topic)) {
            guide.put("parameters", GUIDE_PARAMETERS);
        }
        if ("all".equals(topic) || "common_patterns".equals(topic)) {
            guide.put("common_patterns", GUIDE_COMMON_PATTERNS);
        }

        if (guide.isEmpty()) {
            guide.put("error", "Unknown topic: " + topic
                    + ". Valid topics: overview, node_wiring, parameters, common_patterns, all");
        }

        return guide;
    }

    private Object handleListBrowserSessions(Map<String, Object> args) {
        var sessions = browserSessionRegistry.getActiveSessions();
        List<Map<String, Object>> sessionList = sessions.stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("browserSessionId", s.getBrowserSessionId());
                    m.put("connectedAt", s.getConnectedAt().toString());
                    return m;
                })
                .toList();
        return Map.of("sessions", sessionList, "count", sessionList.size());
    }

    private String resolveTargetSession(String browserSessionId) {
        if (browserSessionId != null && !browserSessionId.isBlank()) {
            if (browserSessionRegistry.getSession(browserSessionId).isEmpty()) {
                var sessions = browserSessionRegistry.getActiveSessions();
                List<String> ids = sessions.stream()
                        .map(BrowserSessionRegistry.BrowserSessionInfo::getBrowserSessionId)
                        .toList();
                throw new IllegalArgumentException(
                        "Browser session '" + browserSessionId + "' not found. Active sessions: " + ids);
            }
            return browserSessionId;
        }

        int count = browserSessionRegistry.getActiveCount();
        if (count == 0) {
            throw new IllegalArgumentException("No browser sessions connected. Open Trellis in a browser first.");
        }
        if (count > 1) {
            List<String> ids = browserSessionRegistry.getActiveSessions().stream()
                    .map(BrowserSessionRegistry.BrowserSessionInfo::getBrowserSessionId)
                    .toList();
            throw new IllegalArgumentException(
                    "Multiple browser sessions active (" + count + "). Specify 'browserSessionId' to target one. Active sessions: " + ids);
        }
        return browserSessionRegistry.getActiveSessions().get(0).getBrowserSessionId();
    }

    @SuppressWarnings("unchecked")
    private Object handleBrowserControl(Map<String, Object> args, String targetSession) {
        String action = (String) args.get("action");
        String message = (String) args.get("message");

        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("'action' is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("'message' is required");
        }

        String targetUrl = (String) args.get("targetUrl");
        Object workflowData = args.get("workflowData");

        if ("navigate".equals(action) && (targetUrl == null || targetUrl.isBlank())) {
            throw new IllegalArgumentException("'targetUrl' is required for navigate action");
        }
        if ("load_workflow".equals(action) && workflowData == null) {
            throw new IllegalArgumentException("'workflowData' is required for load_workflow action");
        }

        // Consent already granted by the global gate — send the browser action
        webSocketService.sendBrowserAction(targetSession, action, targetUrl, workflowData, message);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approved", true);
        result.put("action", action);
        result.put("browserSessionId", targetSession);
        result.put("message", "User approved the request. The action has been executed in the browser.");
        return result;
    }

    private Object handlePushToCanvas(Map<String, Object> args, String targetSession) {
        Object nodes = args.get("nodes");
        Object connections = args.get("connections");

        if (nodes == null) {
            throw new IllegalArgumentException("'nodes' is required");
        }
        if (connections == null) {
            throw new IllegalArgumentException("'connections' is required");
        }

        String name = (String) args.get("name");
        String description = (String) args.get("description");
        String message = (String) args.get("message");

        // Consent already granted by the global gate
        webSocketService.sendAgentCanvasUpdate(targetSession, name, description, nodes, connections, message);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("browserSessionId", targetSession);
        result.put("message", "Workflow data has been pushed to the user's canvas. The user can review and save the changes.");
        return result;
    }

    // --- Helper Methods ---

    private Map<String, Object> toolDef(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        return tool;
    }

    private Map<String, Object> prop(String type, String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    /** Creates a LinkedHashMap from alternating key-value pairs to preserve insertion order. */
    private Map<String, Object> orderedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // --- Guide Content ---

    private static final String GUIDE_OVERVIEW = """
            Trellis is a workflow automation platform. Workflows consist of nodes connected together.
            Each node performs a specific operation (HTTP requests, data transformation, conditionals, etc.).

            Key concepts:
            - A workflow has a list of 'nodes' and a 'connections' map
            - Each node has: id (unique string), name (display label), type (node type from registry),
              typeVersion (integer), parameters (configuration), position (x/y coordinates on canvas)
            - Nodes are connected via the connections map to define data flow
            - Data flows through nodes as List<Map<String, Object>> wrapped as: { "json": <data> }
            - Trigger nodes (webhooks, schedules, etc.) start workflow execution
            - Use trellis_list_node_types and trellis_get_node_type to discover available nodes
            """;

    private static final String GUIDE_NODE_WIRING = """
            Connections define how data flows between nodes. The format is:

            {
              "<sourceNodeId>": {
                "main": [
                  [
                    { "node": "<targetNodeId>", "type": "main", "index": 0 }
                  ]
                ]
              }
            }

            - The outer key is the source node's ID
            - "main" contains an array of output arrays (most nodes have one output at index 0)
            - Each output array lists the connected target nodes
            - "index" is the target node's input index (usually 0)
            - Nodes with multiple outputs (like If/Switch) have multiple arrays in "main"

            Example: A -> B -> C
            {
              "nodeA_id": { "main": [[{ "node": "nodeB_id", "type": "main", "index": 0 }]] },
              "nodeB_id": { "main": [[{ "node": "nodeC_id", "type": "main", "index": 0 }]] }
            }
            """;

    private static final String GUIDE_PARAMETERS = """
            Node parameters configure behavior. Set them in the node's "parameters" object.

            Expression syntax:
            - Use ={{ expression }} to reference data from previous nodes
            - $json.fieldName — access a field from the current item's JSON data
            - $('NodeName').item.json.field — access output from a specific node by name
            - $input.item.json.field — access the direct input to this node

            Common parameter patterns:
            - String values: "parameters": { "url": "https://api.example.com" }
            - Expressions: "parameters": { "url": "={{ $json.baseUrl }}/endpoint" }
            - Options/selects: "parameters": { "method": "POST" }
            - Boolean: "parameters": { "continueOnFail": true }
            - JSON body: "parameters": { "body": "={{ JSON.stringify($json) }}" }

            Use trellis_get_node_type to see all available parameters for a specific node type.
            """;

    private static final String GUIDE_COMMON_PATTERNS = """
            Common workflow patterns:

            1. Webhook -> Process -> Respond:
               Webhook node receives HTTP request, processing nodes transform data,
               RespondToWebhook node sends response back.

            2. Schedule -> Fetch -> Store:
               Schedule trigger fires periodically, HTTP Request fetches data,
               result is stored or forwarded.

            3. Branching with If:
               If node evaluates a condition and routes data to "true" or "false" output.
               True path is output index 0, False path is output index 1.

            4. Looping with LoopOverItems:
               Processes items one at a time. Loop body nodes are connected to output index 1.
               When all items are processed, output index 0 fires with collected results.

            5. Error handling:
               Set "continueOnFail" in node settings to continue on errors.
               Use If nodes to check for error data.

            6. Sub-workflows:
               ExecuteWorkflow node runs another workflow inline, passing data in and receiving results.

            Node positioning:
            - Nodes have position: { x: number, y: number }
            - Typical horizontal spacing: 250px between nodes
            - Typical vertical spacing: 100px for parallel branches
            - Start position around x: 250, y: 300
            """;
}
