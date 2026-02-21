package io.trellis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.entity.WebhookEntity;
import io.trellis.entity.WorkflowEntity;
import io.trellis.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.LinkedHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final ObjectMapper objectMapper;

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
                log.info("Registered webhook: {} {} for workflow {}", method, path, workflow.getId());
            }
        }
    }

    @Transactional
    public void deregisterWorkflowWebhooks(String workflowId) {
        webhookRepository.deleteByWorkflowId(workflowId);
        webhookRepository.flush();
    }

    public Optional<WebhookEntity> resolveWebhook(String method, String path, boolean isTest) {
        return webhookRepository.findByMethodAndPathAndIsTest(method.toUpperCase(), path, isTest);
    }

    @Transactional
    public WebhookEntity registerTestWebhook(String workflowId, String nodeId, String method, String path) {
        WebhookEntity webhook = WebhookEntity.builder()
                .workflowId(workflowId)
                .nodeId(nodeId)
                .method(method.toUpperCase())
                .path(path.startsWith("/") ? path.substring(1) : path)
                .securityChain("none")
                .isTest(true)
                .build();
        return webhookRepository.save(webhook);
    }

    @Transactional
    public void deregisterTestWebhooks(String workflowId) {
        webhookRepository.deleteByWorkflowIdAndIsTest(workflowId, true);
    }

    public List<WebhookEntity> getWorkflowWebhooks(String workflowId) {
        return webhookRepository.findByWorkflowId(workflowId);
    }
}
