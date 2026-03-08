package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.dto.*;
import io.cwc.engine.WorkflowEngine;
import io.cwc.nodes.core.NodeRegistry;
import io.cwc.nodes.core.NodeRegistry.NodeRegistration;
import io.cwc.util.SecurityContextHelper;
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
    private final WorkflowEngine workflowEngine;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final RemoteControlService remoteControlService;
    private final WebSocketService webSocketService;
    private final BrowserSessionRegistry browserSessionRegistry;
    private final SecurityContextHelper securityContextHelper;

    private static final String TOOL_PREFIX = "cwc_";

    // --- Tool Definitions ---

    public List<Map<String, Object>> getSystemToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(toolDef("cwc_list_node_categories",
                "List all available node categories with counts. Use this FIRST to discover what categories exist, then use cwc_list_node_types with a category filter.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap()
                )));

        tools.add(toolDef("cwc_list_node_types",
                "List available node types in the CWC workflow engine. IMPORTANT: There are 520+ nodes — always use the 'category' parameter to filter. Call cwc_list_node_categories FIRST to see available categories. When called with no parameters, returns a category summary instead of all nodes.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "category", prop("string", "Filter by category name (case-insensitive). RECOMMENDED — use cwc_list_node_categories to find valid names."),
                                "search", prop("string", "Search term to filter by type or displayName")
                        )
                )));

        tools.add(toolDef("cwc_get_node_type",
                "Get full details of a specific node type including all parameters, inputs, and outputs. Use this to understand how to configure a node.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "type", prop("string", "The node type identifier (e.g. 'httpRequest', 'code', 'ifNode')")
                        ),
                        "required", List.of("type")
                )));

        tools.add(toolDef("cwc_list_workflows",
                "List all workflows with summary info (id, name, description, published status, tags).",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "projectId", prop("string", "Filter workflows by project ID")
                        )
                )));

        tools.add(toolDef("cwc_get_workflow",
                "Get the full workflow definition including nodes, connections, and settings.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The workflow ID")
                        ),
                        "required", List.of("id")
                )));

        tools.add(toolDef("cwc_create_workflow",
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

        tools.add(toolDef("cwc_update_workflow",
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

        tools.add(toolDef("cwc_list_executions",
                "List workflow executions with optional filtering. Returns id, workflowId, status, mode, startedAt, finishedAt.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "workflowId", prop("string", "Filter by workflow ID"),
                                "status", prop("string", "Filter by status (NEW, RUNNING, SUCCESS, ERROR, CANCELED, WAITING)"),
                                "limit", Map.of("type", "integer", "description", "Max results to return (default 20, max 100)")
                        )
                )));

        tools.add(toolDef("cwc_get_execution",
                "Get execution details including result data and node outputs.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The execution ID")
                        ),
                        "required", List.of("id")
                )));

        tools.add(toolDef("cwc_execute_workflow",
                "Execute a saved workflow and return the results. The workflow must be saved first. " +
                "Returns execution status, result data, and any errors. Use cwc_get_execution for detailed results if needed.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "workflowId", prop("string", "The workflow ID to execute (required)"),
                                "inputData", Map.of("type", "object", "description", "Optional input data to pass to the workflow trigger")
                        ),
                        "required", List.of("workflowId")
                )));

        tools.add(toolDef("cwc_workflow_guide",
                "Returns instructional documentation on how to build CWC workflows, including node structure, connections format, expression syntax, common patterns, and AI agent architecture. IMPORTANT: If you are building an AI Agent or working with AI nodes, request the 'ai_agents' topic FIRST.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "topic", prop("string",
                                        "Topic to get help on: 'overview', 'node_wiring', 'parameters', 'common_patterns', 'ai_agents', or 'all' (default: 'all')")
                        )
                )));

        tools.add(toolDef("cwc_list_browser_sessions",
                "List active browser sessions connected to CWC. Returns session IDs that can be used to target specific browser tabs with canvas pushes or control requests.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap()
                )));

        tools.add(toolDef("cwc_browser_control",
                "Request control of the user's CWC browser tab. Can navigate to a URL within CWC or load a workflow definition for review. Requires user consent via an in-browser approval dialog.",
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

        tools.add(toolDef("cwc_push_to_canvas",
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

        tools.add(toolDef("cwc_publish_workflow",
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

        tools.add(toolDef("cwc_list_projects",
                "List all projects the user has access to. Returns project details including the user's role (PROJECT_PERSONAL_OWNER, PROJECT_ADMIN, PROJECT_EDITOR, PROJECT_VIEWER).",
                Map.of(
                        "type", "object",
                        "properties", orderedMap()
                )));

        tools.add(toolDef("cwc_get_project",
                "Get detailed project info including members (if admin). Returns project details, workflow/credential counts, and the user's role.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The project ID")
                        ),
                        "required", List.of("id")
                )));

        // --- Agent Tools ---

        tools.add(toolDef("cwc_list_agents",
                "List all AI agents with summary info (id, name, icon, description, published status). Agents are workflows with type=AGENT.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "projectId", prop("string", "Filter agents by project ID")
                        )
                )));

        tools.add(toolDef("cwc_get_agent",
                "Get the full agent definition including nodes, connections, settings, and icon.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The agent ID")
                        ),
                        "required", List.of("id")
                )));

        tools.add(toolDef("cwc_create_agent",
                "Create a new AI agent. If no nodes are provided, auto-creates a default AI Agent node with the given system message.",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "name", prop("string", "Agent name (required)"),
                                "description", prop("string", "Agent description"),
                                "icon", prop("string", "Emoji icon for the agent, e.g. '\uD83E\uDD16'"),
                                "projectId", prop("string", "Project ID to create the agent in"),
                                "systemMessage", prop("string", "System message/prompt for the AI Agent node"),
                                "nodes", Map.of("type", "array", "description", "Array of node objects. If omitted, auto-creates an AI Agent node with defaults."),
                                "connections", Map.of("type", "object", "description", "Connection map between nodes")
                        ),
                        "required", List.of("name")
                )));

        tools.add(toolDef("cwc_update_agent",
                "Update an existing AI agent. Only provided fields are updated. The target must be an agent (type=AGENT).",
                Map.of(
                        "type", "object",
                        "properties", orderedMap(
                                "id", prop("string", "The agent ID (required)"),
                                "name", prop("string", "New agent name"),
                                "description", prop("string", "New description"),
                                "icon", prop("string", "New emoji icon"),
                                "nodes", Map.of("type", "array", "description", "Updated node array"),
                                "connections", Map.of("type", "object", "description", "Updated connections map")
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
            "cwc_browser_control",
            "cwc_push_to_canvas",
            "cwc_create_workflow",
            "cwc_update_workflow",
            "cwc_publish_workflow",
            "cwc_create_agent",
            "cwc_update_agent",
            "cwc_execute_workflow"
    );

    private static final Set<String> BROWSER_SESSION_TOOLS = Set.of(
            "cwc_browser_control",
            "cwc_push_to_canvas"
    );

    private boolean requiresConsent(String toolName) {
        return CONSENT_REQUIRED_TOOLS.contains(toolName);
    }

    private boolean needsBrowserSession(String toolName) {
        return BROWSER_SESSION_TOOLS.contains(toolName);
    }

    /**
     * Execute a tool directly without consent checks.
     * Used by the chat agent where the user's message IS the consent.
     */
    public String executeTool(String name, Map<String, Object> arguments) {
        if (arguments == null) arguments = Map.of();
        try {
            String targetSession = null;
            if (needsBrowserSession(name)) {
                targetSession = resolveTargetSession((String) arguments.get("browserSessionId"));
            }
            Object result = dispatchTool(name, arguments, targetSession);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error executing tool directly: {}", name, e);
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : e.getClass().getSimpleName();
            return "{\"error\": \"" + msg + "\"}";
        }
    }

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
            } else if (needsBrowserSession(name)) {
                targetSession = resolveTargetSession((String) arguments.get("browserSessionId"));
            }

            Object result = dispatchTool(name, arguments, targetSession);

            String text = objectMapper.writeValueAsString(result);
            return Map.of("content", List.of(Map.of("type", "text", "text", text)));
        } catch (Exception e) {
            log.error("Error handling system tool call: {}", name, e);
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Error: " + e.getMessage())),
                    "isError", true);
        }
    }

    private Object dispatchTool(String name, Map<String, Object> arguments, String targetSession) {
        return switch (name) {
            case "cwc_list_node_categories" -> handleListNodeCategories(arguments);
            case "cwc_list_node_types" -> handleListNodeTypes(arguments);
            case "cwc_get_node_type" -> handleGetNodeType(arguments);
            case "cwc_list_projects" -> handleListProjects(arguments);
            case "cwc_get_project" -> handleGetProject(arguments);
            case "cwc_list_workflows" -> handleListWorkflows(arguments);
            case "cwc_get_workflow" -> handleGetWorkflow(arguments);
            case "cwc_create_workflow" -> handleCreateWorkflow(arguments);
            case "cwc_update_workflow" -> handleUpdateWorkflow(arguments);
            case "cwc_list_executions" -> handleListExecutions(arguments);
            case "cwc_get_execution" -> handleGetExecution(arguments);
            case "cwc_execute_workflow" -> handleExecuteWorkflow(arguments);
            case "cwc_workflow_guide" -> handleWorkflowGuide(arguments);
            case "cwc_list_browser_sessions" -> handleListBrowserSessions(arguments);
            case "cwc_browser_control" -> handleBrowserControl(arguments, targetSession);
            case "cwc_push_to_canvas" -> handlePushToCanvas(arguments, targetSession);
            case "cwc_publish_workflow" -> handlePublishWorkflow(arguments);
            case "cwc_list_agents" -> handleListAgents(arguments);
            case "cwc_get_agent" -> handleGetAgent(arguments);
            case "cwc_create_agent" -> handleCreateAgent(arguments);
            case "cwc_update_agent" -> handleUpdateAgent(arguments);
            default -> throw new IllegalArgumentException("Unknown tool: " + name);
        };
    }

    private String generateToolDescription(String name, Map<String, Object> args) {
        return switch (name) {
            case "cwc_list_node_types" -> {
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
            case "cwc_get_node_type" -> "Get details for node type '" + args.getOrDefault("type", "?") + "'";
            case "cwc_list_workflows" -> {
                String projectId = (String) args.get("projectId");
                yield projectId != null ? "List workflows in project " + projectId : "List all workflows";
            }
            case "cwc_get_workflow" -> "Get workflow definition for '" + args.getOrDefault("id", "?") + "'";
            case "cwc_create_workflow" -> "Create a new workflow named '" + args.getOrDefault("name", "?") + "'";
            case "cwc_update_workflow" -> "Update workflow '" + args.getOrDefault("id", "?") + "'";
            case "cwc_list_executions" -> {
                String wfId = (String) args.get("workflowId");
                yield wfId != null ? "List executions for workflow " + wfId : "List recent workflow executions";
            }
            case "cwc_get_execution" -> "Get execution details for '" + args.getOrDefault("id", "?") + "'";
            case "cwc_execute_workflow" -> "Execute workflow '" + args.getOrDefault("workflowId", "?") + "'";
            case "cwc_workflow_guide" -> "Get workflow building guide" +
                    (args.get("topic") != null ? " (topic: " + args.get("topic") + ")" : "");
            case "cwc_list_node_categories" -> "List all node categories with counts";
            case "cwc_list_projects" -> "List all accessible projects";
            case "cwc_get_project" -> "Get project details for '" + args.getOrDefault("id", "?") + "'";
            case "cwc_list_browser_sessions" -> "List active browser sessions";
            case "cwc_browser_control" -> {
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
            case "cwc_push_to_canvas" -> {
                String wfName = (String) args.get("name");
                yield wfName != null
                        ? "Push workflow '" + wfName + "' to the editor canvas"
                        : "Push workflow data to the editor canvas";
            }
            case "cwc_publish_workflow" -> {
                String vName = (String) args.get("versionName");
                yield vName != null
                        ? "Publish workflow '" + args.getOrDefault("id", "?") + "' as '" + vName + "'"
                        : "Publish workflow '" + args.getOrDefault("id", "?") + "' as a new version";
            }
            case "cwc_list_agents" -> {
                String projId = (String) args.get("projectId");
                yield projId != null ? "List agents in project " + projId : "List all AI agents";
            }
            case "cwc_get_agent" -> "Get agent definition for '" + args.getOrDefault("id", "?") + "'";
            case "cwc_create_agent" -> "Create a new AI agent named '" + args.getOrDefault("name", "?") + "'";
            case "cwc_update_agent" -> "Update agent '" + args.getOrDefault("id", "?") + "'";
            default -> "Execute " + name;
        };
    }

    // --- Tool Handlers ---

    private Object handleListNodeTypes(Map<String, Object> args) {
        String category = (String) args.get("category");
        String search = (String) args.get("search");

        // When no filters provided, return category summary instead of all 520+ nodes
        if ((category == null || category.isBlank()) && (search == null || search.isBlank())) {
            Map<String, Long> counts = nodeRegistry.getCategoriesWithCounts();
            List<Map<String, Object>> categories = counts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("category", e.getKey());
                        m.put("nodeCount", e.getValue());
                        return m;
                    })
                    .toList();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("categories", categories);
            summary.put("totalCategories", categories.size());
            summary.put("totalNodes", counts.values().stream().mapToLong(Long::longValue).sum());
            summary.put("hint", "Use 'category' to list nodes in a specific category, or 'search' to find by keyword.");
            return summary;
        }

        Collection<NodeRegistration> nodes;
        if (category != null && !category.isBlank()) {
            nodes = nodeRegistry.getNodesByCategory(category);
            // If no exact match, suggest similar categories
            if (nodes.isEmpty()) {
                Map<String, Long> allCategories = nodeRegistry.getCategoriesWithCounts();
                String lowerCategory = category.toLowerCase();
                List<String> suggestions = allCategories.keySet().stream()
                        .filter(c -> c.toLowerCase().contains(lowerCategory) || lowerCategory.contains(c.toLowerCase()))
                        .sorted()
                        .toList();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("nodeTypes", List.of());
                result.put("count", 0);
                if (!suggestions.isEmpty()) {
                    result.put("message", "No nodes found in category '" + category + "'. Did you mean: " + String.join(", ", suggestions));
                } else {
                    result.put("message", "No nodes found in category '" + category + "'. Use cwc_list_node_categories to see valid category names.");
                }
                return result;
            }
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

    private Object handleListNodeCategories(Map<String, Object> args) {
        Map<String, Long> counts = nodeRegistry.getCategoriesWithCounts();
        List<Map<String, Object>> categories = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("nodeCount", e.getValue());
                    return m;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        result.put("totalCategories", categories.size());
        result.put("totalNodes", counts.values().stream().mapToLong(Long::longValue).sum());
        return result;
    }

    private Object handleListProjects(Map<String, Object> args) {
        String currentUserId = securityContextHelper.getCurrentUserId();
        List<ProjectResponse> projects = projectService.listProjects();

        List<Map<String, Object>> result = projects.stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getName());
                    m.put("type", p.getType());
                    m.put("description", p.getDescription());
                    m.put("contextPath", p.getContextPath());
                    m.put("workflowCount", p.getWorkflowCount());
                    m.put("credentialCount", p.getCredentialCount());
                    m.put("userRole", projectService.getUserRoleString(p.getId(), currentUserId));
                    return m;
                })
                .toList();

        return Map.of("projects", result, "count", result.size());
    }

    private Object handleGetProject(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("'id' is required");
        }

        String currentUserId = securityContextHelper.getCurrentUserId();
        ProjectResponse project = projectService.getProject(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", project.getId());
        result.put("name", project.getName());
        result.put("type", project.getType());
        result.put("description", project.getDescription());
        result.put("contextPath", project.getContextPath());
        result.put("workflowCount", project.getWorkflowCount());
        result.put("credentialCount", project.getCredentialCount());
        result.put("userRole", projectService.getUserRoleString(id, currentUserId));
        if (project.getMembers() != null) {
            result.put("members", project.getMembers());
        }
        result.put("createdAt", project.getCreatedAt());
        result.put("updatedAt", project.getUpdatedAt());
        return result;
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

        // Pre-load project names to avoid N+1 queries
        Map<String, String> projectNames = projectService.listProjects().stream()
                .collect(Collectors.toMap(ProjectResponse::getId, ProjectResponse::getName, (a, b) -> a));

        List<Map<String, Object>> result = workflows.stream()
                .filter(wf -> !"AGENT".equals(wf.getType()))
                .map(wf -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", wf.getId());
                    m.put("name", wf.getName());
                    m.put("description", wf.getDescription());
                    m.put("projectId", wf.getProjectId());
                    m.put("projectName", projectNames.getOrDefault(wf.getProjectId(), null));
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

        // Resolve project name
        String projectName = null;
        if (wf.getProjectId() != null) {
            try {
                projectName = projectService.getProject(wf.getProjectId()).getName();
            } catch (Exception e) {
                // Project may have been deleted
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("description", wf.getDescription());
        result.put("projectId", wf.getProjectId());
        result.put("projectName", projectName);
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
                    + "Use cwc_publish_workflow to create a new version instead.");
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
                workflowId, null, status, 0, limit);

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

    @SuppressWarnings("unchecked")
    private Object handleExecuteWorkflow(Map<String, Object> args) {
        String workflowId = (String) args.get("workflowId");
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("'workflowId' is required");
        }

        Map<String, Object> inputData = (Map<String, Object>) args.get("inputData");
        String executionId = workflowEngine.startExecution(workflowId, inputData);

        // Poll for completion (max 60 seconds)
        ExecutionResponse result = null;
        for (int i = 0; i < 60; i++) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            result = executionService.getExecution(executionId);
            if (!"running".equalsIgnoreCase(result.getStatus()) && !"new".equalsIgnoreCase(result.getStatus())) {
                break;
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("executionId", executionId);
        response.put("workflowId", workflowId);
        if (result != null && !"running".equalsIgnoreCase(result.getStatus()) && !"new".equalsIgnoreCase(result.getStatus())) {
            response.put("status", result.getStatus());
            response.put("resultData", result.getResultData());
            response.put("errorMessage", result.getErrorMessage());
            response.put("startedAt", result.getStartedAt());
            response.put("finishedAt", result.getFinishedAt());
        } else {
            response.put("status", "timeout");
            response.put("message", "Execution did not complete within 60 seconds. Use cwc_get_execution with id '" + executionId + "' to check later.");
        }
        return response;
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
        if ("all".equals(topic) || "ai_agents".equals(topic)) {
            guide.put("ai_agents", GUIDE_AI_AGENTS);
        }

        if (guide.isEmpty()) {
            guide.put("error", "Unknown topic: " + topic
                    + ". Valid topics: overview, node_wiring, parameters, common_patterns, ai_agents, all");
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
            throw new IllegalArgumentException("No browser sessions connected. Open CWC in a browser first.");
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

        // Build workflow data payload (same shape as browser_control load_workflow)
        Map<String, Object> workflowData = new LinkedHashMap<>();
        workflowData.put("nodes", nodes);
        workflowData.put("connections", connections);
        if (name != null) workflowData.put("name", name);
        if (description != null) workflowData.put("description", description);

        // Route through agent-control topic (always subscribed) instead of agent-canvas topic
        // which is only active when the workflow editor is open
        webSocketService.sendBrowserAction(targetSession, "push_canvas", null, workflowData,
                message != null ? message : "Workflow data pushed to canvas");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("browserSessionId", targetSession);
        result.put("message", "Workflow data has been pushed to the user's canvas. The user can review and save the changes.");
        return result;
    }

    // --- Agent Handlers ---

    private Object handleListAgents(Map<String, Object> args) {
        String projectId = (String) args.get("projectId");

        List<WorkflowResponse> workflows;
        if (projectId != null && !projectId.isBlank()) {
            workflows = workflowService.listWorkflowsByProject(projectId);
        } else {
            workflows = workflowService.listWorkflows();
        }

        Map<String, String> projectNames = projectService.listProjects().stream()
                .collect(Collectors.toMap(ProjectResponse::getId, ProjectResponse::getName, (a, b) -> a));

        List<Map<String, Object>> result = workflows.stream()
                .filter(wf -> "AGENT".equals(wf.getType()))
                .map(wf -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", wf.getId());
                    m.put("name", wf.getName());
                    m.put("icon", wf.getIcon());
                    m.put("description", wf.getDescription());
                    m.put("projectId", wf.getProjectId());
                    m.put("projectName", projectNames.getOrDefault(wf.getProjectId(), null));
                    m.put("published", wf.isPublished());
                    m.put("createdAt", wf.getCreatedAt());
                    m.put("updatedAt", wf.getUpdatedAt());
                    return m;
                })
                .toList();

        return Map.of("agents", result, "count", result.size());
    }

    private Object handleGetAgent(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("'id' is required");
        }

        WorkflowResponse wf = workflowService.getWorkflow(id);
        if (!"AGENT".equals(wf.getType())) {
            throw new IllegalArgumentException("'" + id + "' is not an agent (type=" + wf.getType() + "). Use cwc_get_workflow instead.");
        }

        String projectName = null;
        if (wf.getProjectId() != null) {
            try {
                projectName = projectService.getProject(wf.getProjectId()).getName();
            } catch (Exception e) {
                // Project may have been deleted
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("icon", wf.getIcon());
        result.put("description", wf.getDescription());
        result.put("projectId", wf.getProjectId());
        result.put("projectName", projectName);
        result.put("published", wf.isPublished());
        result.put("currentVersion", wf.getCurrentVersion());
        result.put("nodes", wf.getNodes());
        result.put("connections", wf.getConnections());
        result.put("settings", wf.getSettings());
        result.put("createdAt", wf.getCreatedAt());
        result.put("updatedAt", wf.getUpdatedAt());
        return result;
    }

    private Object handleCreateAgent(Map<String, Object> args) {
        String name = (String) args.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("'name' is required");
        }

        WorkflowCreateRequest request = new WorkflowCreateRequest();
        request.setName(name);
        request.setType("AGENT");
        request.setDescription((String) args.get("description"));
        request.setIcon((String) args.get("icon"));
        request.setProjectId((String) args.get("projectId"));

        if (args.containsKey("nodes")) {
            request.setNodes(args.get("nodes"));
            request.setConnections(args.get("connections"));
        } else {
            // Auto-create default AI Agent node (mirrors frontend behavior)
            String systemMessage = (String) args.get("systemMessage");
            if (systemMessage == null || systemMessage.isBlank()) {
                systemMessage = "You are a helpful assistant.";
            }

            String agentNodeId = UUID.randomUUID().toString();
            List<Map<String, Object>> defaultNodes = List.of(Map.of(
                    "id", agentNodeId,
                    "name", "AI Agent",
                    "type", "aiAgent",
                    "typeVersion", 1,
                    "parameters", Map.of(
                            "systemMessage", systemMessage,
                            "prompt", "={{$json.chatInput}}",
                            "maxIterations", 10
                    ),
                    "position", List.of(400, 300)
            ));
            request.setNodes(defaultNodes);
            request.setConnections(Map.of());
        }

        WorkflowResponse wf = workflowService.createWorkflow(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("icon", wf.getIcon());
        result.put("description", wf.getDescription());
        result.put("message", "Agent created successfully");
        result.put("editorUrl", "/agent/" + wf.getId());
        return result;
    }

    private Object handleUpdateAgent(Map<String, Object> args) {
        String id = (String) args.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("'id' is required");
        }

        WorkflowResponse existing = workflowService.getWorkflow(id);
        if (!"AGENT".equals(existing.getType())) {
            throw new IllegalArgumentException("'" + id + "' is not an agent (type=" + existing.getType() + "). Use cwc_update_workflow instead.");
        }
        if (existing.isPublished()) {
            throw new IllegalArgumentException(
                    "Agent '" + id + "' is published and cannot be modified directly. "
                    + "Use cwc_publish_workflow to create a new version instead.");
        }

        WorkflowUpdateRequest request = new WorkflowUpdateRequest();
        if (args.containsKey("name")) request.setName((String) args.get("name"));
        if (args.containsKey("description")) request.setDescription((String) args.get("description"));
        if (args.containsKey("icon")) request.setIcon((String) args.get("icon"));
        if (args.containsKey("nodes")) request.setNodes(args.get("nodes"));
        if (args.containsKey("connections")) request.setConnections(args.get("connections"));

        WorkflowResponse wf = workflowService.updateWorkflow(id, request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", wf.getId());
        result.put("name", wf.getName());
        result.put("icon", wf.getIcon());
        result.put("message", "Agent updated successfully");
        result.put("editorUrl", "/agent/" + wf.getId());
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
            CWC is a workflow automation platform. Workflows consist of nodes connected together.
            Each node performs a specific operation (HTTP requests, data transformation, conditionals, etc.).

            Key concepts:
            - A workflow has a list of 'nodes' and a 'connections' map
            - Each node has: id (unique string), name (display label), type (node type from registry),
              typeVersion (integer), parameters (configuration), position (x/y coordinates on canvas)
            - Nodes are connected via the connections map to define data flow
            - Data flows through nodes as List<Map<String, Object>> wrapped as: { "json": <data> }
            - Trigger nodes (webhooks, schedules, etc.) start workflow execution
            - Use cwc_list_node_types and cwc_get_node_type to discover available nodes
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

            --- AI NODE CONNECTIONS (summary — see 'ai_agents' topic for full guide) ---

            AI nodes (aiAgent, aiSubAgent) have special AI input handles that use connection
            types OTHER than "main": ai_languageModel, ai_memory, and ai_tool.

            For AI connections, the SOURCE is always the sub-node and the TARGET is the agent.
            The connection type key matches the AI input handle type on the agent.

            CRITICAL — DUAL INDEX RULE:
            Each AI connection needs TWO entries in the inner target array:
            - Entry 1: index: 0  (always 0 — required by the UI connection handler)
            - Entry 2: index: N  (the handle's FIXED visual position on the agent node)

            The handle positions are FIXED and NEVER change regardless of how many connections exist:
            - ai_languageModel handle = position 0 → single entry {index: 0} (both values are 0)
            - ai_memory handle       = position 1 → two entries: {index: 0} AND {index: 1}
            - ai_tool handle         = position 2 → two entries: {index: 0} AND {index: 2}

            IMPORTANT: The index value for ai_tool is ALWAYS 2 — even when connecting multiple
            tools to the same agent. Do NOT increment it to 3, 4, 5, etc. Every tool connection
            uses index 0 and index 2 identically.

            Quick example (agent with model, memory, think tool, code tool, and sub-agent as tool):
            {
              "model1":    { "ai_languageModel": [[{ "node": "agent1", "type": "ai_languageModel", "index": 0 }]] },
              "memory1":   { "ai_memory": [[{ "node": "agent1", "type": "ai_memory", "index": 0 }, { "node": "agent1", "type": "ai_memory", "index": 1 }]] },
              "think1":    { "ai_tool": [[{ "node": "agent1", "type": "ai_tool", "index": 0 }, { "node": "agent1", "type": "ai_tool", "index": 2 }]] },
              "codeTool1": { "ai_tool": [[{ "node": "agent1", "type": "ai_tool", "index": 0 }, { "node": "agent1", "type": "ai_tool", "index": 2 }]] },
              "subAgent1": { "ai_tool": [[{ "node": "agent1", "type": "ai_tool", "index": 0 }, { "node": "agent1", "type": "ai_tool", "index": 2 }]] }
            }

            For full AI agent architecture, node types, sub-agent wiring, and multi-agent
            examples, use: cwc_workflow_guide with topic 'ai_agents'
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

            Use cwc_get_node_type to see all available parameters for a specific node type.
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

            7. AI Agent with tools:
               An aiAgent node needs at minimum a Chat Model (ai_languageModel connection).
               Optionally add Memory (ai_memory) and Tools (ai_tool).
               Sub-nodes connect TO the agent — the sub-node is the source, the agent is the target.
               Use aiSubAgent nodes as tools on a parent aiAgent for multi-agent orchestration.
               Each aiSubAgent can have its own Chat Model, Memory, and Tools.
               IMPORTANT: See the 'ai_agents' topic (cwc_workflow_guide with topic 'ai_agents')
               for the full AI connection format, dual-index rule, and multi-agent examples.

            Node positioning:
            - Nodes have position: { x: number, y: number }
            - Typical horizontal spacing: 250px between nodes
            - Typical vertical spacing: 100px for parallel branches
            - Start position around x: 250, y: 300
            """;

    // ── Comprehensive AI Agent guide ──────────────────────────────────────────
    private static final String GUIDE_AI_AGENTS = """

            ╔══════════════════════════════════════════════════════════════════╗
            ║                   CWC AI AGENT ARCHITECTURE                     ║
            ╚══════════════════════════════════════════════════════════════════╝

            This guide covers everything needed to build AI Agents: node types,
            connection wiring, the dual-index rule, sub-agent orchestration,
            and complete working examples.

            ═══════════════════════════════════════════════════════════════════
            1. AI NODE TYPES — WHAT EACH NODE IS
            ═══════════════════════════════════════════════════════════════════

            ┌─────────────────────────────────────────────────────────────────┐
            │ AGENT NODES (these RECEIVE connections from sub-nodes)          │
            ├─────────────────────────────────────────────────────────────────┤
            │                                                                 │
            │  aiAgent (AI Agent)                                             │
            │    The primary autonomous agent node. Processes user prompts    │
            │    using a language model, can use tools in a loop, and         │
            │    maintains conversation memory.                               │
            │    Inputs:  main (data), ai_languageModel, ai_memory, ai_tool  │
            │    Outputs: main (data)                                         │
            │    Key params: systemMessage, prompt, maxIterations             │
            │    REQUIRED: Must have at least one ai_languageModel connected  │
            │                                                                 │
            │  aiSubAgent (AI Sub Agent)                                      │
            │    A child agent that connects as a TOOL to a parent agent.     │
            │    The parent agent can delegate tasks to it by name.           │
            │    Inputs:  ai_languageModel, ai_memory, ai_tool               │
            │    Outputs: ai_tool (this is how it connects to its parent!)    │
            │    Key params: agentName, agentDescription, systemMessage,      │
            │                maxIterations                                    │
            │    NOTE: No "main" input — it receives work from its parent     │
            │    agent via the tool interface, not from the data flow.        │
            │    The parent sees it as a callable tool identified by          │
            │    agentName, and uses agentDescription to decide when to       │
            │    delegate to it.                                              │
            │                                                                 │
            └─────────────────────────────────────────────────────────────────┘

            ┌─────────────────────────────────────────────────────────────────┐
            │ SUB-NODES (these CONNECT TO agent nodes via AI handles)         │
            ├─────────────────────────────────────────────────────────────────┤
            │                                                                 │
            │  --- Chat Models (connect to ai_languageModel handle) ---       │
            │                                                                 │
            │  anthropicChatModel — Claude models (Sonnet, Haiku, Opus)       │
            │    Params: model (select), temperature, maxTokens               │
            │    Credentials: anthropicApi                                    │
            │    Output type: ai_languageModel                                │
            │                                                                 │
            │  (Use cwc_list_node_types with category "AI / Chat Models"      │
            │   to discover all available chat model providers)               │
            │                                                                 │
            │  --- Memory (connect to ai_memory handle) ---                   │
            │                                                                 │
            │  windowBufferMemory — In-process chat history (no credentials)  │
            │    Params: sessionId, contextWindowLength                        │
            │    Output type: ai_memory                                       │
            │                                                                 │
            │  (Use cwc_list_node_types with category "AI / Memory"           │
            │   to discover all available memory providers)                   │
            │                                                                 │
            │  --- Tools (connect to ai_tool handle) ---                      │
            │                                                                 │
            │  toolThink — Reasoning scratchpad for the agent (no params)     │
            │    Output type: ai_tool                                         │
            │                                                                 │
            │  codeTool — Executes JavaScript or Python code                  │
            │    Params: language ("js"/"python"), code (the script)          │
            │    The agent passes input via the 'input' variable              │
            │    Output type: ai_tool                                         │
            │                                                                 │
            │  aiSubAgent — Another agent acting as a tool (see above)        │
            │    Output type: ai_tool                                         │
            │                                                                 │
            │  (Use cwc_list_node_types with category "AI / Tools"            │
            │   to discover all available tool types)                         │
            │                                                                 │
            └─────────────────────────────────────────────────────────────────┘


            ═══════════════════════════════════════════════════════════════════
            2. AI CONNECTION RULES
            ═══════════════════════════════════════════════════════════════════

            RULE 1 — Direction: The sub-node is the SOURCE, the agent is the TARGET.

              The connection map key is the sub-node's ID.
              The inner array entries point at the agent node.
              This is the OPPOSITE of how you might intuit it — the model/memory/tool
              nodes "plug into" the agent, so THEY are the source of the connection.

            RULE 2 — Connection type key matches the AI handle type.

              The key under the source node ID is the AI handle type:
              - "ai_languageModel" for chat model connections
              - "ai_memory" for memory connections
              - "ai_tool" for tool connections (including sub-agents!)

            RULE 3 — DUAL INDEX (the most common source of errors!)

              Each AI connection's inner array needs TWO target objects:
              - One with index: 0  (always literal 0 — required by the UI)
              - One with index: N  (the FIXED handle position on the agent)

              The three AI handles on an agent have FIXED positions:
              ┌──────────────────────┬──────────────┬───────────────────────┐
              │ Handle type          │ Position     │ Inner array entries    │
              ├──────────────────────┼──────────────┼───────────────────────┤
              │ ai_languageModel     │ 0            │ [{index:0}]           │
              │ ai_memory            │ 1            │ [{index:0},{index:1}] │
              │ ai_tool              │ 2            │ [{index:0},{index:2}] │
              └──────────────────────┴──────────────┴───────────────────────┘

              ai_languageModel only needs one entry because position 0 = index 0.
              ai_memory needs two entries: index 0 AND index 1.
              ai_tool needs two entries: index 0 AND index 2.

              ⚠ COMMON MISTAKE: Do NOT increment the tool index for multiple tools!
              If you connect 3 tools to an agent, ALL THREE use index 0 + index 2.
              The index is the HANDLE POSITION (always 2 for tools), not a counter.

              ❌ WRONG — incrementing index per tool:
              "tool1": { "ai_tool": [[{..., "index": 0}, {..., "index": 2}]] }
              "tool2": { "ai_tool": [[{..., "index": 0}, {..., "index": 3}]] }  ← WRONG!
              "tool3": { "ai_tool": [[{..., "index": 0}, {..., "index": 4}]] }  ← WRONG!

              ✅ CORRECT — all tools use index 2:
              "tool1": { "ai_tool": [[{..., "index": 0}, {..., "index": 2}]] }
              "tool2": { "ai_tool": [[{..., "index": 0}, {..., "index": 2}]] }
              "tool3": { "ai_tool": [[{..., "index": 0}, {..., "index": 2}]] }

            RULE 4 — Every target entry must include node, type, AND index.

              Each entry in the inner array is:
              { "node": "<agentNodeId>", "type": "<ai_handle_type>", "index": <N> }


            ═══════════════════════════════════════════════════════════════════
            3. CONNECTION FORMAT TEMPLATES
            ═══════════════════════════════════════════════════════════════════

            --- Connecting a Chat Model to an agent/sub-agent ---
            "<modelNodeId>": {
              "ai_languageModel": [[
                { "node": "<agentNodeId>", "type": "ai_languageModel", "index": 0 }
              ]]
            }

            --- Connecting Memory to an agent/sub-agent ---
            "<memoryNodeId>": {
              "ai_memory": [[
                { "node": "<agentNodeId>", "type": "ai_memory", "index": 0 },
                { "node": "<agentNodeId>", "type": "ai_memory", "index": 1 }
              ]]
            }

            --- Connecting a Tool to an agent/sub-agent ---
            (works for toolThink, codeTool, aiSubAgent, or any ai_tool output node)
            "<toolNodeId>": {
              "ai_tool": [[
                { "node": "<agentNodeId>", "type": "ai_tool", "index": 0 },
                { "node": "<agentNodeId>", "type": "ai_tool", "index": 2 }
              ]]
            }

            --- Connecting a Sub-Agent as a tool to a parent agent ---
            (aiSubAgent outputs ai_tool — same format as any tool connection)
            "<subAgentNodeId>": {
              "ai_tool": [[
                { "node": "<parentAgentNodeId>", "type": "ai_tool", "index": 0 },
                { "node": "<parentAgentNodeId>", "type": "ai_tool", "index": 2 }
              ]]
            }


            ═══════════════════════════════════════════════════════════════════
            4. EXAMPLE — SIMPLE AI AGENT (model + memory + 1 tool)
            ═══════════════════════════════════════════════════════════════════

            Nodes:
            [
              { "id": "agent1", "name": "My Agent", "type": "aiAgent", "typeVersion": 1,
                "position": [500, 300],
                "parameters": { "systemMessage": "You are a helpful assistant.",
                                "prompt": "{{input}}", "maxIterations": 10 } },
              { "id": "model1", "name": "Claude", "type": "anthropicChatModel", "typeVersion": 1,
                "position": [200, 150],
                "parameters": { "model": "claude-sonnet-4-20250514", "temperature": 0.7,
                                "maxTokens": 1024 } },
              { "id": "memory1", "name": "Memory", "type": "windowBufferMemory", "typeVersion": 1,
                "position": [200, 300],
                "parameters": { "sessionId": "default", "contextWindowLength": 10 } },
              { "id": "think1", "name": "Think", "type": "toolThink", "typeVersion": 1,
                "position": [200, 450], "parameters": {} }
            ]

            Connections:
            {
              "model1":  { "ai_languageModel": [[{ "node": "agent1", "type": "ai_languageModel", "index": 0 }]] },
              "memory1": { "ai_memory": [[{ "node": "agent1", "type": "ai_memory", "index": 0 }, { "node": "agent1", "type": "ai_memory", "index": 1 }]] },
              "think1":  { "ai_tool": [[{ "node": "agent1", "type": "ai_tool", "index": 0 }, { "node": "agent1", "type": "ai_tool", "index": 2 }]] }
            }


            ═══════════════════════════════════════════════════════════════════
            5. EXAMPLE — MULTI-AGENT ORCHESTRATION (parent + 2 sub-agents)
            ═══════════════════════════════════════════════════════════════════

            Architecture:
              parentAgent (aiAgent)
                ├── model1 (anthropicChatModel) ─── ai_languageModel
                ├── memory1 (windowBufferMemory) ── ai_memory
                ├── think1 (toolThink) ──────────── ai_tool
                ├── subAgent1 (aiSubAgent) ──────── ai_tool  ← sub-agent AS a tool
                │     ├── model2 (anthropicChatModel) ─── ai_languageModel
                │     └── code1 (codeTool) ────────────── ai_tool
                └── subAgent2 (aiSubAgent) ──────── ai_tool  ← sub-agent AS a tool
                      └── model3 (anthropicChatModel) ─── ai_languageModel

            Key insight: An aiSubAgent OUTPUTS ai_tool, so it connects to the parent
            agent's ai_tool handle using the SAME format as toolThink or codeTool.
            But the aiSubAgent also RECEIVES its own sub-node connections (model, memory,
            tools) on its own AI input handles, using the same dual-index rule.

            Nodes:
            [
              { "id": "parentAgent", "name": "Supervisor", "type": "aiAgent", "typeVersion": 1,
                "position": [600, 300],
                "parameters": { "systemMessage": "You are a supervisor agent. Delegate tasks to researcher for facts and to creative for ideas.",
                                "prompt": "{{input}}", "maxIterations": 15 } },
              { "id": "model1", "name": "Supervisor Model", "type": "anthropicChatModel", "typeVersion": 1,
                "position": [200, 150],
                "parameters": { "model": "claude-sonnet-4-20250514", "temperature": 0.7, "maxTokens": 2048 } },
              { "id": "memory1", "name": "Supervisor Memory", "type": "windowBufferMemory", "typeVersion": 1,
                "position": [200, 300],
                "parameters": { "sessionId": "supervisor", "contextWindowLength": 15 } },
              { "id": "think1", "name": "Think", "type": "toolThink", "typeVersion": 1,
                "position": [200, 450], "parameters": {} },
              { "id": "subAgent1", "name": "Researcher", "type": "aiSubAgent", "typeVersion": 1,
                "position": [400, 600],
                "parameters": { "agentName": "researcher",
                                "agentDescription": "Research agent for factual questions and analysis",
                                "systemMessage": "You are a precise research agent.", "maxIterations": 8 } },
              { "id": "model2", "name": "Researcher Model", "type": "anthropicChatModel", "typeVersion": 1,
                "position": [200, 750],
                "parameters": { "model": "claude-sonnet-4-20250514", "temperature": 0.3, "maxTokens": 2048 } },
              { "id": "code1", "name": "Calculator", "type": "codeTool", "typeVersion": 1,
                "position": [400, 750],
                "parameters": { "language": "js", "code": "try { return String(eval(input)); } catch(e) { return 'Error: ' + e.message; }" } },
              { "id": "subAgent2", "name": "Creative", "type": "aiSubAgent", "typeVersion": 1,
                "position": [800, 600],
                "parameters": { "agentName": "creative",
                                "agentDescription": "Creative agent for brainstorming and ideas",
                                "systemMessage": "You are a creative thinker.", "maxIterations": 8 } },
              { "id": "model3", "name": "Creative Model", "type": "anthropicChatModel", "typeVersion": 1,
                "position": [800, 750],
                "parameters": { "model": "claude-sonnet-4-20250514", "temperature": 1.0, "maxTokens": 2048 } }
            ]

            Connections:
            {
              "model1":    { "ai_languageModel": [[{ "node": "parentAgent", "type": "ai_languageModel", "index": 0 }]] },
              "memory1":   { "ai_memory": [[{ "node": "parentAgent", "type": "ai_memory", "index": 0 }, { "node": "parentAgent", "type": "ai_memory", "index": 1 }]] },
              "think1":    { "ai_tool": [[{ "node": "parentAgent", "type": "ai_tool", "index": 0 }, { "node": "parentAgent", "type": "ai_tool", "index": 2 }]] },
              "subAgent1": { "ai_tool": [[{ "node": "parentAgent", "type": "ai_tool", "index": 0 }, { "node": "parentAgent", "type": "ai_tool", "index": 2 }]] },
              "subAgent2": { "ai_tool": [[{ "node": "parentAgent", "type": "ai_tool", "index": 0 }, { "node": "parentAgent", "type": "ai_tool", "index": 2 }]] },
              "model2":    { "ai_languageModel": [[{ "node": "subAgent1", "type": "ai_languageModel", "index": 0 }]] },
              "code1":     { "ai_tool": [[{ "node": "subAgent1", "type": "ai_tool", "index": 0 }, { "node": "subAgent1", "type": "ai_tool", "index": 2 }]] },
              "model3":    { "ai_languageModel": [[{ "node": "subAgent2", "type": "ai_languageModel", "index": 0 }]] }
            }

            NOTE: subAgent1, subAgent2, and think1 ALL connect to parentAgent's ai_tool
            handle with the SAME indices: index 0 and index 2. The index does NOT vary
            per tool — it is the fixed handle position.


            ═══════════════════════════════════════════════════════════════════
            6. CREATING AGENTS vs WORKFLOWS
            ═══════════════════════════════════════════════════════════════════

            - Use cwc_create_agent (not cwc_create_workflow) when building AI agents.
              This sets the type to AGENT and provides the agent chat UI.
            - cwc_create_agent can auto-create a default aiAgent node if no nodes are
              provided — just pass a systemMessage. But for sub-agents and tools,
              you must provide the full nodes and connections arrays.
            - Use cwc_create_workflow for non-AI automation workflows.
            - Agents appear in cwc_list_agents; workflows appear in cwc_list_workflows.
            - After creating an agent, navigate to it with cwc_browser_control using
              the editorUrl returned by cwc_create_agent.


            ═══════════════════════════════════════════════════════════════════
            7. COMMON MISTAKES & TROUBLESHOOTING
            ═══════════════════════════════════════════════════════════════════

            ❌ MISTAKE: Incrementing ai_tool index per tool (index 2, 3, 4...)
            ✅ FIX: Always use index 0 + index 2 for EVERY ai_tool connection.

            ❌ MISTAKE: Using "main" connection type for AI sub-nodes
            ✅ FIX: Use ai_languageModel, ai_memory, or ai_tool as the connection key.

            ❌ MISTAKE: Making the agent the source and sub-node the target
            ✅ FIX: Sub-node is ALWAYS the source (map key), agent is the target (in array).

            ❌ MISTAKE: Forgetting the dual index entries for ai_memory or ai_tool
            ✅ FIX: ai_memory needs [{index:0},{index:1}], ai_tool needs [{index:0},{index:2}].

            ❌ MISTAKE: Connecting aiSubAgent via "main" to parent agent
            ✅ FIX: aiSubAgent outputs ai_tool — connect it the same way as toolThink/codeTool.

            ❌ MISTAKE: Not giving aiSubAgent its own Chat Model
            ✅ FIX: Each aiSubAgent can have its own model, memory, and tools. The model
               is optional only if the parent agent's model is inherited (check node docs).
               In practice, always give each sub-agent its own anthropicChatModel.

            ❌ MISTAKE: Creating an agent with cwc_create_workflow
            ✅ FIX: Use cwc_create_agent to get type=AGENT and the agent chat UI.

            """;
}
