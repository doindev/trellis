package io.cwc.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps MCP tool names + arguments to their corresponding REST API calls.
 * The browser uses this to execute the API on the user's behalf.
 */
public class ToolApiMapping {

    /** Tools that bypass consent — documentation and browser session listing. */
    public static final Set<String> BYPASS_CONSENT_TOOLS = Set.of(
            "cwc_workflow_guide",
            "cwc_list_node_types",
            "cwc_get_node_type",
            "cwc_list_node_categories",
            "cwc_list_browser_sessions"
    );

    /** Tools that execute locally in the browser (not via REST proxy). */
    public static final Set<String> BROWSER_LOCAL_TOOLS = Set.of(
            "cwc_browser_control",
            "cwc_push_to_canvas"
    );

    /**
     * Resolves a tool name + arguments to the REST API call spec the browser should execute.
     * Returns null for bypass tools and browser-local tools.
     */
    public static ApiCallSpec resolve(String toolName, Map<String, Object> args) {
        if (BYPASS_CONSENT_TOOLS.contains(toolName) || BROWSER_LOCAL_TOOLS.contains(toolName)) {
            return null;
        }

        return switch (toolName) {
            // --- READ operations ---
            case "cwc_list_projects" -> new ApiCallSpec("GET", "/api/projects", null, null, "read", false, null);

            case "cwc_get_project" -> new ApiCallSpec("GET",
                    "/api/projects/" + str(args, "id"), null, null, "read", false, null);

            case "cwc_list_workflows" -> {
                Map<String, String> qp = new LinkedHashMap<>();
                if (args.containsKey("projectId")) qp.put("projectId", str(args, "projectId"));
                yield new ApiCallSpec("GET", "/api/workflows", null, qp.isEmpty() ? null : qp, "read", false, null);
            }

            case "cwc_get_workflow" -> new ApiCallSpec("GET",
                    "/api/workflows/" + str(args, "id"), null, null, "read", false, null);

            case "cwc_list_executions" -> {
                Map<String, String> qp = new LinkedHashMap<>();
                if (args.containsKey("workflowId")) qp.put("workflowId", str(args, "workflowId"));
                if (args.containsKey("status")) qp.put("status", str(args, "status"));
                if (args.containsKey("limit")) qp.put("size", str(args, "limit"));
                yield new ApiCallSpec("GET", "/api/executions", null, qp.isEmpty() ? null : qp, "read", false, null);
            }

            case "cwc_get_execution" -> new ApiCallSpec("GET",
                    "/api/executions/" + str(args, "id"), null, null, "read", false, null);

            case "cwc_list_agents" -> {
                Map<String, String> qp = new LinkedHashMap<>();
                qp.put("type", "AGENT");
                if (args.containsKey("projectId")) qp.put("projectId", str(args, "projectId"));
                yield new ApiCallSpec("GET", "/api/workflows", null, qp, "read", false, null);
            }

            case "cwc_get_agent" -> new ApiCallSpec("GET",
                    "/api/workflows/" + str(args, "id"), null, null, "read", false, null);

            // --- WRITE operations ---
            case "cwc_create_project" -> new ApiCallSpec("POST", "/api/projects",
                    buildBody(args, "name", "description", "contextPath", "icon"),
                    null, "write", false, null);

            case "cwc_update_project" -> new ApiCallSpec("PATCH",
                    "/api/projects/" + str(args, "id"),
                    buildBody(args, "name", "description", "contextPath", "icon"),
                    null, "write", false, null);

            case "cwc_create_workflow" -> new ApiCallSpec("POST", "/api/workflows",
                    buildBody(args, "name", "description", "projectId", "nodes", "connections",
                            "type", "mcpEnabled", "mcpDescription", "mcpInputSchema", "mcpOutputSchema"),
                    null, "write", false, null);

            case "cwc_update_workflow" -> new ApiCallSpec("PUT",
                    "/api/workflows/" + str(args, "id"),
                    buildBody(args, "name", "description", "nodes", "connections",
                            "type", "mcpEnabled", "mcpDescription", "mcpInputSchema", "mcpOutputSchema"),
                    null, "write", false, null);

            case "cwc_publish_workflow" -> new ApiCallSpec("POST",
                    "/api/workflows/" + str(args, "id") + "/publish",
                    buildBody(args, "versionName", "description"),
                    null, "write", false, null);

            case "cwc_execute_workflow" -> new ApiCallSpec("POST",
                    "/api/workflows/" + str(args, "workflowId") + "/run",
                    buildBody(args, "inputData"),
                    null, "write", true,
                    "/api/executions/{executionId}");

            case "cwc_create_agent" -> {
                Map<String, Object> body = buildBody(args, "name", "description", "icon", "projectId",
                        "nodes", "connections");
                body.put("type", "AGENT");
                yield new ApiCallSpec("POST", "/api/workflows", body, null, "write", false, null);
            }

            case "cwc_update_agent" -> new ApiCallSpec("PUT",
                    "/api/workflows/" + str(args, "id"),
                    buildBody(args, "name", "description", "icon", "nodes", "connections"),
                    null, "write", false, null);

            default -> throw new IllegalArgumentException("No API mapping for tool: " + toolName);
        };
    }

    private static String str(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : "";
    }

    private static Map<String, Object> buildBody(Map<String, Object> args, String... keys) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : keys) {
            if (args.containsKey(key)) {
                body.put(key, args.get(key));
            }
        }
        return body;
    }

    /**
     * Specification for a REST API call the browser should execute.
     */
    public record ApiCallSpec(
            String method,
            String path,
            Map<String, Object> body,
            Map<String, String> queryParams,
            String category,
            boolean requiresPolling,
            String pollingPath
    ) {}
}
