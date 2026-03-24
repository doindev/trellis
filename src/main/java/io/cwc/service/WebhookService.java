package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.config.ProjectContextPathFilter;
import io.cwc.engine.ExpressionEvaluator;
import io.cwc.entity.ProjectEntity;
import io.cwc.entity.WebhookEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.WebhookRepository;
import io.cwc.security.WebhookSecurityRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.*;
import java.util.LinkedHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    public record WebhookMatch(WebhookEntity webhook, Map<String, Object> pathVariables) {}

    private final WebhookRepository webhookRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final WebhookSecurityRegistry webhookSecurityRegistry;
    private final ProjectContextPathFilter projectContextPathFilter;
    private final ExpressionEvaluator expressionEvaluator;
    private final VariableService variableService;

    @PostConstruct
    public void initializeSecurityRegistry() {
        loadSecurityRegistryFromDatabase();
    }

    /**
     * Clears the in-memory webhook security registry and reloads all
     * patterns from the database. Used for cluster sync when another
     * instance changes webhook registrations.
     */
    public void refreshSecurityRegistry() {
        webhookSecurityRegistry.clearAll();
        loadSecurityRegistryFromDatabase();
        log.info("Refreshed webhook security registry from database");
    }

    private void loadSecurityRegistryFromDatabase() {
        List<WebhookEntity> webhooks = webhookRepository.findByIsTest(false);
        for (WebhookEntity webhook : webhooks) {
            String chain = webhook.getSecurityChain();
            if (chain != null && !"none".equals(chain)) {
                webhookSecurityRegistry.register(chain, webhook.getMethod(), webhook.getPath());
            }
        }
        log.info("Loaded webhook security registry with {} entries",
                webhooks.stream().filter(w -> w.getSecurityChain() != null && !"none".equals(w.getSecurityChain())).count());
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public void registerWorkflowWebhooks(WorkflowEntity workflow) {
        deregisterWorkflowWebhooks(workflow.getId());

        Object nodesObj = workflow.getNodes();
        if (nodesObj == null) return;

        List<Map<String, Object>> nodes;
        if (nodesObj instanceof List) {
            nodes = (List<Map<String, Object>>) nodesObj;
        } else if (nodesObj instanceof String) {
            try {
                nodes = objectMapper.readValue((String) nodesObj,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                log.error("Failed to parse workflow nodes JSON", e);
                return;
            }
        } else {
            return;
        }

        for (Map<String, Object> node : nodes) {
            String type = (String) node.get("type");
            if (!"webhook".equals(type) && !"formTrigger".equals(type) && !"chatTrigger".equals(type)) continue;

            String nodeId = (String) node.get("id");
            Map<String, Object> parameters = (Map<String, Object>) node.getOrDefault("parameters", Map.of());
            String rawPath = (String) parameters.getOrDefault("path", "");
            String path = resolvePathExpressions(rawPath, workflow.getProjectId());
            String authentication = (String) parameters.getOrDefault("authentication",
                    (String) parameters.getOrDefault("securityChain", "none"));

            if (path.isEmpty()) continue;

            List<String> requiredRoles = extractRoles(parameters);
            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

            // Prepend project context path if configured, otherwise use workflow ID for chat triggers
            String contextPath = resolveContextPath(workflow.getProjectId());
            if (contextPath != null) {
                normalizedPath = contextPath + "/" + normalizedPath;
            } else if ("chatTrigger".equals(type)) {
                normalizedPath = workflow.getId() + "/" + normalizedPath;
            }

            if ("chatTrigger".equals(type)) {
                // Only register webhooks when public=true
                Object publicObj = parameters.get("public");
                boolean isPublic = Boolean.TRUE.equals(publicObj) || "true".equals(String.valueOf(publicObj));
                if (!isPublic) continue;

                String mode = (String) parameters.getOrDefault("mode", "hostedChat");

                // Extract options (nested collection parameters)
                @SuppressWarnings("unchecked")
                Map<String, Object> options = parameters.get("options") instanceof Map
                        ? (Map<String, Object>) parameters.get("options") : Map.of();
                String responseMode = (String) options.getOrDefault("responseMode", "lastNode");

                // Build chat configuration for webhookOptions
                Map<String, Object> chatConfig = new LinkedHashMap<>();
                chatConfig.put("nodeType", "chatTrigger");
                chatConfig.put("mode", mode);
                chatConfig.put("title", options.getOrDefault("title", "Hi there!"));
                chatConfig.put("subtitle", options.getOrDefault("subtitle", "Start a chat. We're here to help you 24/7."));
                chatConfig.put("initialMessages", parameters.getOrDefault("initialMessages", ""));
                chatConfig.put("inputPlaceholder", options.getOrDefault("inputPlaceholder", "Type your question.."));
                chatConfig.put("showWelcomeScreen", options.getOrDefault("showWelcomeScreen", false));
                chatConfig.put("getStarted", options.getOrDefault("getStarted", "New Conversation"));
                chatConfig.put("loadPreviousSession", options.getOrDefault("loadPreviousSession", "notSupported"));
                chatConfig.put("allowFileUploads", options.getOrDefault("allowFileUploads", false));
                chatConfig.put("allowedFilesMimeTypes", options.getOrDefault("allowedFilesMimeTypes", "*"));
                chatConfig.put("allowedOrigins", options.getOrDefault("allowedOrigins", "*"));
                chatConfig.put("customCss", options.getOrDefault("customCss", ""));

                if ("hostedChat".equals(mode)) {
                    // Register GET for serving chat page
                    WebhookEntity getWebhook = WebhookEntity.builder()
                            .workflowId(workflow.getId())
                            .nodeId(nodeId)
                            .method("GET")
                            .path(normalizedPath)
                            .securityChain(authentication)
                            .responseMode("chatTrigger")
                            .webhookOptions(chatConfig)
                            .requiredRoles(requiredRoles)
                            .build();
                    webhookRepository.save(getWebhook);
                    if (!"none".equals(authentication)) {
                        webhookSecurityRegistry.register(authentication, "GET", normalizedPath);
                    }
                }

                // Register POST for chat messages (both hostedChat and webhook modes)
                WebhookEntity postWebhook = WebhookEntity.builder()
                        .workflowId(workflow.getId())
                        .nodeId(nodeId)
                        .method("POST")
                        .path(normalizedPath)
                        .securityChain(authentication)
                        .responseMode(responseMode)
                        .webhookOptions(chatConfig)
                        .requiredRoles(requiredRoles)
                        .build();
                webhookRepository.save(postWebhook);
                if (!"none".equals(authentication)) {
                    webhookSecurityRegistry.register(authentication, "POST", normalizedPath);
                }

                log.info("Registered chat trigger: {} ({}) for workflow {}", path, mode, workflow.getId());
            } else if ("formTrigger".equals(type)) {
                // Register both GET (serve form) and POST (submit) for form triggers
                // Store form configuration in webhookOptions for rendering
                Map<String, Object> formConfig = new LinkedHashMap<>();
                formConfig.put("formTitle", parameters.getOrDefault("formTitle", "Form"));
                formConfig.put("formDescription", parameters.getOrDefault("formDescription", ""));
                formConfig.put("formFields", parameters.get("formFields"));
                formConfig.put("buttonLabel", parameters.getOrDefault("buttonLabel", "Submit"));
                formConfig.put("nodeType", "formTrigger");

                // Register GET for form rendering
                WebhookEntity getWebhook = WebhookEntity.builder()
                        .workflowId(workflow.getId())
                        .nodeId(nodeId)
                        .method("GET")
                        .path(normalizedPath)
                        .securityChain(authentication)
                        .responseMode("formTrigger")
                        .webhookOptions(formConfig)
                        .requiredRoles(requiredRoles)
                        .build();
                webhookRepository.save(getWebhook);
                if (!"none".equals(authentication)) {
                    webhookSecurityRegistry.register(authentication, "GET", normalizedPath);
                }

                // Register POST for form submission
                WebhookEntity postWebhook = WebhookEntity.builder()
                        .workflowId(workflow.getId())
                        .nodeId(nodeId)
                        .method("POST")
                        .path(normalizedPath)
                        .securityChain(authentication)
                        .responseMode("formTrigger")
                        .webhookOptions(formConfig)
                        .requiredRoles(requiredRoles)
                        .build();
                webhookRepository.save(postWebhook);
                if (!"none".equals(authentication)) {
                    webhookSecurityRegistry.register(authentication, "POST", normalizedPath);
                }

                log.info("Registered form trigger: {} for workflow {}", path, workflow.getId());
            } else {
                // Register webhook for the configured HTTP method
                String method = (String) parameters.getOrDefault("httpMethod", "GET");
                String responseMode = (String) parameters.getOrDefault("responseMode", "onReceived");
                Object nodeOptions = parameters.get("options");
                WebhookEntity webhook = WebhookEntity.builder()
                        .workflowId(workflow.getId())
                        .nodeId(nodeId)
                        .method(method.toUpperCase())
                        .path(normalizedPath)
                        .securityChain(authentication)
                        .responseMode(responseMode)
                        .webhookOptions(nodeOptions)
                        .requiredRoles(requiredRoles)
                        .build();

                webhookRepository.save(webhook);
                if (!"none".equals(authentication)) {
                    webhookSecurityRegistry.register(authentication, method.toUpperCase(), normalizedPath);
                }
                log.info("Registered webhook: {} {} for workflow {}", method, path, workflow.getId());
            }
        }

        projectContextPathFilter.refreshCache();
    }

    @Transactional
    public void deregisterWorkflowWebhooks(String workflowId) {
        List<WebhookEntity> existing = webhookRepository.findByWorkflowId(workflowId);
        for (WebhookEntity webhook : existing) {
            String chain = webhook.getSecurityChain();
            if (chain != null && !"none".equals(chain)) {
                webhookSecurityRegistry.deregister(chain, webhook.getMethod(), webhook.getPath());
            }
        }
        webhookRepository.deleteByWorkflowId(workflowId);
        webhookRepository.flush();
    }

    public Optional<WebhookMatch> resolveWebhook(String method, String path, boolean isTest) {
        String upperMethod = method.toUpperCase();

        // Fast path: exact match
        Optional<WebhookEntity> exact = webhookRepository.findByMethodAndPathAndIsTest(upperMethod, path, isTest);
        if (exact.isPresent()) {
            return Optional.of(new WebhookMatch(exact.get(), Map.of()));
        }

        // Fallback: pattern matching for parameterized paths
        List<WebhookEntity> candidates = webhookRepository.findByMethodAndIsTestAndPathContaining(upperMethod, isTest, "{");
        PathPatternParser parser = new PathPatternParser();
        PathContainer requestPath = PathContainer.parsePath("/" + path);

        for (WebhookEntity candidate : candidates) {
            try {
                PathPattern pattern = parser.parse("/" + candidate.getPath());
                PathPattern.PathMatchInfo matchInfo = pattern.matchAndExtract(requestPath);
                if (matchInfo != null) {
                    Map<String, Object> typedVars = new LinkedHashMap<>();
                    matchInfo.getUriVariables().forEach((key, value) -> {
                        typedVars.put(key, convertNumeric(value));
                    });
                    return Optional.of(new WebhookMatch(candidate, typedVars));
                }
            } catch (Exception e) {
                log.warn("Failed to parse webhook path pattern '{}': {}", candidate.getPath(), e.getMessage());
            }
        }

        return Optional.empty();
    }

    private Object convertNumeric(String value) {
        if (value != null && value.matches("\\d+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return value;
            }
        }
        return value;
    }

    @Transactional
    public WebhookEntity registerTestWebhook(String workflowId, String nodeId, String method, String path, String contextPath) {
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        if (contextPath != null && !contextPath.isBlank()) {
            normalizedPath = contextPath + "/" + normalizedPath;
        }
        WebhookEntity webhook = WebhookEntity.builder()
                .workflowId(workflowId)
                .nodeId(nodeId)
                .method(method.toUpperCase())
                .path(normalizedPath)
                .securityChain("none")
                .isTest(true)
                .build();
        return webhookRepository.save(webhook);
    }

    @Transactional
    public void deregisterTestWebhooks(String workflowId) {
        webhookRepository.deleteByWorkflowIdAndIsTest(workflowId, true);
    }

    public String resolveContextPath(String projectId) {
        if (projectId == null) return null;
        return projectRepository.findById(projectId)
                .map(ProjectEntity::getContextPath)
                .orElse(null);
    }

    public List<WebhookEntity> getWorkflowWebhooks(String workflowId) {
        return webhookRepository.findByWorkflowId(workflowId);
    }

    /**
     * Extracts role names from the FIXED_COLLECTION "roles" parameter.
     * Expects a list of maps with a "roleName" key, e.g.:
     * [{"roleName": "admin"}, {"roleName": "editor"}]
     * Returns a flat list of role name strings, or null if empty/absent.
     */
    /**
     * Resolves expressions in a webhook path using $env (system environment
     * variables) and $vars (CWC variables). Only these two sources are
     * available at registration time since there is no execution context yet.
     */
    private String resolvePathExpressions(String path, String projectId) {
        if (path == null || !path.contains("{{")) return path;

        Map<String, String> envVars = new LinkedHashMap<>(System.getenv());
        Map<String, String> variables = variableService.getVariablesForProject(projectId);

        ExpressionEvaluator.ExpressionContext ctx = ExpressionEvaluator.ExpressionContext.builder()
                .currentItemData(Map.of())
                .inputItems(List.of())
                .envVars(envVars)
                .variables(variables)
                .build();

        Object resolved = expressionEvaluator.resolveExpressions(path, ctx);
        return resolved != null ? String.valueOf(resolved) : path;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Map<String, Object> parameters) {
        Object rolesObj = parameters.get("roles");
        if (rolesObj == null) return null;

        List<Map<String, Object>> roleEntries;
        if (rolesObj instanceof List) {
            roleEntries = (List<Map<String, Object>>) rolesObj;
        } else if (rolesObj instanceof Map) {
            // FIXED_COLLECTION may come wrapped as {"values": [...]}
            Object values = ((Map<String, Object>) rolesObj).get("values");
            if (values instanceof List) {
                roleEntries = (List<Map<String, Object>>) values;
            } else {
                return null;
            }
        } else {
            return null;
        }

        List<String> roles = new ArrayList<>();
        for (Map<String, Object> entry : roleEntries) {
            Object roleName = entry.get("roleName");
            if (roleName != null && !roleName.toString().isBlank()) {
                roles.add(roleName.toString().trim());
            }
        }
        return roles.isEmpty() ? null : roles;
    }
}
