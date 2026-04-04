package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.SwaggerSettingsDto;
import io.cwc.entity.SwaggerSettingsEntity;
import io.cwc.entity.TagEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.exception.BadRequestException;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.SwaggerSettingsRepository;
import io.cwc.repository.TagRepository;
import io.cwc.repository.WorkflowRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(name = "org.springdoc.core.configuration.SpringDocConfiguration")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "cwc.features.swagger.enabled", havingValue = "true", matchIfMissing = true)
public class SwaggerSettingsService {

    private final SwaggerSettingsRepository repository;
    private final WorkflowRepository workflowRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;

    @Setter(onMethod_ = {@Autowired, @Lazy})
    private SettingsWritebackService settingsWritebackService;

    public SwaggerSettingsDto getSettings() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .map(e -> SwaggerSettingsDto.builder()
                        .enabled(e.isEnabled())
                        .apiTitle(e.getApiTitle())
                        .apiDescription(e.getApiDescription())
                        .apiVersion(e.getApiVersion())
                        .build())
                .orElse(SwaggerSettingsDto.builder().build());
    }

    @Transactional
    public SwaggerSettingsDto updateSettings(SwaggerSettingsDto dto) {
        SwaggerSettingsEntity entity = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(SwaggerSettingsEntity::new);
        entity.setEnabled(dto.isEnabled());
        if (dto.getApiTitle() != null) entity.setApiTitle(dto.getApiTitle());
        if (dto.getApiDescription() != null) entity.setApiDescription(dto.getApiDescription());
        if (dto.getApiVersion() != null) entity.setApiVersion(dto.getApiVersion());
        repository.save(entity);
        if (settingsWritebackService != null) settingsWritebackService.writeSettings();
        return getSettings();
    }

    public List<Map<String, Object>> getAllWorkflowsWithSwaggerStatus() {
        Map<String, String> projectNames = projectRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        io.cwc.entity.ProjectEntity::getId,
                        io.cwc.entity.ProjectEntity::getName));

        return workflowRepository.findAll().stream()
                .map(wf -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", wf.getId());
                    map.put("name", wf.getName());
                    map.put("description", wf.getDescription());
                    map.put("swaggerEnabled", wf.isSwaggerEnabled());
                    map.put("published", wf.isPublished());
                    map.put("hasWebhookNode", hasWebhookNode(wf));
                    map.put("projectId", wf.getProjectId());
                    map.put("projectName", wf.getProjectId() != null
                            ? projectNames.getOrDefault(wf.getProjectId(), null)
                            : null);
                    return map;
                })
                .toList();
    }

    @Transactional
    public void setWorkflowSwaggerEnabled(String workflowId, boolean enabled) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        if (enabled && !workflow.isPublished()) {
            throw new BadRequestException("Only published workflows can be enabled for Swagger access");
        }
        if (enabled && !hasWebhookNode(workflow)) {
            throw new BadRequestException("Workflow must contain at least one Webhook node to enable Swagger access");
        }
        workflow.setSwaggerEnabled(enabled);

        TagEntity swaggerTag = tagRepository.findByName("swagger")
                .orElseGet(() -> tagRepository.save(TagEntity.builder().name("swagger").build()));
        if (enabled) {
            workflow.getTags().add(swaggerTag);
        } else {
            workflow.getTags().remove(swaggerTag);
        }

        workflowRepository.save(workflow);
    }

    private boolean hasWebhookNode(WorkflowEntity workflow) {
        Object nodes = workflow.getNodes();
        if (nodes instanceof List<?> nodeList) {
            return nodeList.stream().anyMatch(n -> {
                if (n instanceof Map<?, ?> map) {
                    return "webhook".equals(map.get("type"));
                }
                return false;
            });
        }
        return false;
    }
}
