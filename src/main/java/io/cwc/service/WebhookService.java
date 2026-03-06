package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.config.ProjectContextPathFilter;
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
            if (!"webhook".equals(type) && !"formTrigger".equals(type)) continue;

            String nodeId = (String) node.get("id");
            Map<String, Object> parameters = (Map<String, Object>) node.getOrDefault("parameters", Map.of());
            String path = (String) parameters.getOrDefault("path", "");
            String authentication = (String) parameters.getOrDefault("authentication",
                    (String) parameters.getOrDefault("securityChain", "none"));

            if (path.isEmpty()) continue;

            String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

            // Prepend project context path if configured
            String contextPath = resolveContextPath(workflow.getProjectId());
            if (contextPath != null) {
                normalizedPath = contextPath + "/" + normalizedPath;
            }

            if ("formTrigger".equals(type)) {
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
}
