package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.dto.*;
import io.cwc.entity.*;
import io.cwc.repository.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports projects and workflows to config file formats (ZIP or JSON bundle).
 * Credentials are exported with {{env:...}} placeholder templates — never with actual secrets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigExportService {

    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final CredentialRepository credentialRepository;
    private final CredentialEncryptionService encryptionService;
    private final VariableRepository variableRepository;
    private final CacheDefinitionRepository cacheDefinitionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Exports only the project settings (no workflows) as a ProjectConfigFile.
     */
    public ProjectConfigFile exportSettingsOnly(String projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new io.cwc.exception.NotFoundException("Project not found: " + projectId));
        return buildProjectConfig(project);
    }

    /**
     * Exports a project as a single JSON bundle with embedded workflows.
     */
    public ProjectExportBundle exportAsBundle(String projectId) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new io.cwc.exception.NotFoundException("Project not found: " + projectId));

        ProjectConfigFile projectConfig = buildProjectConfig(project);
        List<WorkflowConfigFile> workflows = buildWorkflowConfigs(projectId);

        return ProjectExportBundle.builder()
                .exportedAt(Instant.now())
                .project(projectConfig)
                .workflows(workflows)
                .build();
    }

    /**
     * Exports a project as a ZIP archive matching the convention directory structure.
     */
    public byte[] exportAsZip(String projectId) throws IOException {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new io.cwc.exception.NotFoundException("Project not found: " + projectId));

        ProjectConfigFile projectConfig = buildProjectConfig(project);
        List<WorkflowConfigFile> workflows = buildWorkflowConfigs(projectId);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // project.json
            zos.putNextEntry(new ZipEntry("project.json"));
            zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(projectConfig));
            zos.closeEntry();

            // workflows/*.json
            for (WorkflowConfigFile wf : workflows) {
                String filename = slugify(wf.getName()) + ".json";
                zos.putNextEntry(new ZipEntry("workflows/" + filename));
                zos.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(wf));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private ProjectConfigFile buildProjectConfig(ProjectEntity project) {
        String projectId = project.getId();
        String projectConfigId = project.getConfigId() != null ? project.getConfigId() : slugify(project.getName());

        ProjectConfigFile config = new ProjectConfigFile();
        config.setConfigId(projectConfigId);
        config.setName(project.getName());
        config.setType(project.getType().name());
        config.setContextPath(project.getContextPath());
        config.setDescription(project.getDescription());

        // Variables
        List<ProjectConfigFile.VariableConfig> variables = new ArrayList<>();
        for (VariableEntity v : variableRepository.findByProjectId(projectId)) {
            ProjectConfigFile.VariableConfig vc = new ProjectConfigFile.VariableConfig();
            vc.setKey(v.getKey());
            if (v.getSourcePlaceholder() != null) {
                vc.setValue(v.getSourcePlaceholder());
            } else {
                vc.setValue(generateVariablePlaceholder(projectConfigId, v.getKey()));
            }
            vc.setType(v.getType());
            variables.add(vc);
        }
        config.setVariables(variables.isEmpty() ? null : variables);

        // Credentials
        List<ProjectConfigFile.CredentialConfig> credentials = new ArrayList<>();
        for (CredentialEntity c : credentialRepository.findByProjectId(projectId)) {
            ProjectConfigFile.CredentialConfig cc = new ProjectConfigFile.CredentialConfig();
            String ref = c.getConfigId() != null ? c.getConfigId() : slugify(c.getName());
            cc.setRef(ref);
            cc.setName(c.getName());
            cc.setType(c.getType());

            // Build placeholder templates for credential data fields
            Map<String, Object> dataTemplate = buildCredentialPlaceholders(
                    projectConfigId, ref, c);
            cc.setData(dataTemplate);
            credentials.add(cc);
        }
        config.setCredentials(credentials.isEmpty() ? null : credentials);

        // Caches
        List<ProjectConfigFile.CacheConfig> caches = new ArrayList<>();
        for (CacheDefinitionEntity cache : cacheDefinitionRepository.findByProjectIdOrderByCreatedAtDesc(projectId)) {
            ProjectConfigFile.CacheConfig cc = new ProjectConfigFile.CacheConfig();
            cc.setName(cache.getName());
            cc.setDescription(cache.getDescription());
            cc.setMaxSize(cache.getMaxSize());
            cc.setTtlSeconds(cache.getTtlSeconds());
            caches.add(cc);
        }
        config.setCaches(caches.isEmpty() ? null : caches);

        return config;
    }

    @SuppressWarnings("unchecked")
    private List<WorkflowConfigFile> buildWorkflowConfigs(String projectId) {
        List<WorkflowConfigFile> workflows = new ArrayList<>();
        for (WorkflowEntity w : workflowRepository.findByProjectId(projectId)) {
            if (w.isArchived()) continue;

            WorkflowConfigFile wf = new WorkflowConfigFile();
            wf.setConfigId(w.getConfigId() != null ? w.getConfigId() : slugify(w.getName()));
            wf.setName(w.getName());
            wf.setDescription(w.getDescription());
            wf.setType(w.getType());
            wf.setPublished(w.isPublished() ? true : null);
            wf.setIcon(w.getIcon());

            // Convert credential IDs and agent definition IDs to portable refs in nodes
            if (w.getNodes() instanceof List) {
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) w.getNodes();
                nodes = convertCredentialIdsToRefs(nodes, projectId);
                nodes = convertAgentDefinitionIdsToConfigIds(nodes);
                wf.setNodes(nodes);
            }

            if (w.getConnections() instanceof Map) {
                wf.setConnections((Map<String, Object>) w.getConnections());
            }
            if (w.getSettings() instanceof Map) {
                wf.setSettings((Map<String, Object>) w.getSettings());
            }

            if (w.isMcpEnabled()) wf.setMcpEnabled(true);
            if (w.getMcpDescription() != null) wf.setMcpDescription(w.getMcpDescription());
            if (w.getMcpInputSchema() != null) wf.setMcpInputSchema(w.getMcpInputSchema());
            if (w.getMcpOutputSchema() != null) wf.setMcpOutputSchema(w.getMcpOutputSchema());
            if (w.isSwaggerEnabled()) wf.setSwaggerEnabled(true);

            workflows.add(wf);
        }
        return workflows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertCredentialIdsToRefs(
            List<Map<String, Object>> nodes, String projectId) {

        // Build id -> ref lookup
        Map<String, String> idToRef = new HashMap<>();
        for (CredentialEntity c : credentialRepository.findByProjectId(projectId)) {
            String ref = c.getConfigId() != null ? c.getConfigId() : slugify(c.getName());
            idToRef.put(c.getId(), ref);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> copy = new LinkedHashMap<>(node);
            Object credentials = copy.get("credentials");
            if (credentials instanceof Map) {
                Map<String, Object> credMap = new LinkedHashMap<>((Map<String, Object>) credentials);
                for (Map.Entry<String, Object> entry : credMap.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> credRef = (Map<String, Object>) entry.getValue();
                        String id = (String) credRef.get("id");
                        if (id != null && idToRef.containsKey(id)) {
                            credMap.put(entry.getKey(), Map.of("ref", idToRef.get(id)));
                        }
                    }
                }
                copy.put("credentials", credMap);
            }
            result.add(copy);
        }
        return result;
    }

    /**
     * Converts agentDefinitionId database IDs to stable configIds in aiAgent node parameters.
     * This makes exported workflows portable across database restarts.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertAgentDefinitionIdsToConfigIds(List<Map<String, Object>> nodes) {
        // Build id -> configId lookup for all AGENT-type workflows
        Map<String, String> idToConfigId = new HashMap<>();
        for (WorkflowEntity agent : workflowRepository.findAll()) {
            if ("AGENT".equals(agent.getType())) {
                String configId = agent.getConfigId() != null ? agent.getConfigId() : slugify(agent.getName());
                idToConfigId.put(agent.getId(), configId);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> copy = new LinkedHashMap<>(node);
            Object params = copy.get("parameters");
            if (params instanceof Map) {
                Map<String, Object> paramMap = new LinkedHashMap<>((Map<String, Object>) params);
                Object agentDefId = paramMap.get("agentDefinitionId");
                if (agentDefId instanceof String id && idToConfigId.containsKey(id)) {
                    paramMap.put("agentDefinitionId", idToConfigId.get(id));
                    copy.put("parameters", paramMap);
                }
            }
            result.add(copy);
        }
        return result;
    }

    private Map<String, Object> buildCredentialPlaceholders(
            String projectConfigId, String credRef, CredentialEntity credential) {

        // If we have stored source placeholders, try to parse them
        if (credential.getSourcePlaceholder() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> stored = objectMapper.readValue(
                        credential.getSourcePlaceholder(), Map.class);
                if (!stored.isEmpty()) return stored;
            } catch (Exception ignored) {}
        }

        // Generate placeholders from actual field names
        Map<String, Object> template = new LinkedHashMap<>();
        try {
            Map<String, Object> decrypted = encryptionService.decrypt(credential.getData());
            for (String field : decrypted.keySet()) {
                String placeholder = generateCredentialPlaceholder(projectConfigId, credRef, field);
                template.put(field, placeholder);
            }
        } catch (Exception e) {
            log.debug("Could not decrypt credential {} for export template: {}", credRef, e.getMessage());
        }
        return template;
    }

    private String generateCredentialPlaceholder(String projectConfigId, String credRef, String fieldName) {
        return "{{env:" + toUpperSnake(projectConfigId) + "_" + toUpperSnake(credRef) + "_" + toUpperSnake(fieldName) + "}}";
    }

    private String generateVariablePlaceholder(String projectConfigId, String varKey) {
        return "{{env:" + toUpperSnake(projectConfigId) + "_" + toUpperSnake(varKey) + "}}";
    }

    private String toUpperSnake(String input) {
        return input.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }

    private String slugify(String input) {
        if (input == null) return "unnamed";
        return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
