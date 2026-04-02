package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.cwc.dto.AiSettingsDto;
import io.cwc.dto.ProjectResponse;
import io.cwc.entity.ProjectEntity;
import io.cwc.entity.ProjectRelationEntity;
import io.cwc.entity.UserEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.entity.ProjectEntity.ProjectType;
import io.cwc.repository.CredentialRepository;
import io.cwc.repository.ProjectRelationRepository;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.UserRepository;
import io.cwc.repository.WorkflowRepository;
import io.cwc.service.AiSettingsService;
import io.cwc.service.ProjectService;

import org.springframework.core.annotation.Order;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ProjectRelationRepository projectRelationRepository;
    private final ProjectService projectService;
    private final WorkflowRepository workflowRepository;
    private final CredentialRepository credentialRepository;
    private final AiSettingsService aiSettingsService;
    private final ProjectContextPathFilter contextPathFilter;

    @Override
    public void run(String... args) {
        UserEntity owner;
        if (userRepository.count() == 0) {
            owner = UserEntity.builder()
                    .email("owner@cwc.local")
                    .firstName("Default")
                    .lastName("Owner")
                    .passwordHash("$placeholder$")
                    .role("owner")
                    .build();
            userRepository.save(owner);
            log.info("Seeded default owner user: {}", owner.getEmail());
        } else {
            owner = userRepository.findAll().get(0);
        }

        // Create personal project if none exists
        List<ProjectEntity> personalProjects = projectRepository.findByType(ProjectType.PERSONAL);
        String personalProjectId;
        if (personalProjects.isEmpty()) {
            ProjectResponse personalProject = projectService.createPersonalProject(owner.getId());
            personalProjectId = personalProject.getId();
            log.info("Seeded personal project: {}", personalProjectId);
        } else {
            personalProjectId = personalProjects.get(0).getId();
            // Backfill contextPath on existing personal projects that don't have one
            for (ProjectEntity pp : personalProjects) {
                if (pp.getContextPath() == null) {
                    String ownerId = projectRelationRepository.findByProjectId(pp.getId()).stream()
                            .filter(r -> r.getRole() == ProjectRelationEntity.ProjectRole.PROJECT_PERSONAL_OWNER)
                            .map(ProjectRelationEntity::getUserId)
                            .findFirst()
                            .orElse(null);
                    if (ownerId != null) {
                        pp.setContextPath(ownerId);
                        projectRepository.save(pp);
                        log.info("Backfilled context path '{}' on personal project {}", ownerId, pp.getId());
                    }
                }
            }
        }

        // Backfill workflows without a projectId
        workflowRepository.findByProjectIdIsNull().forEach(w -> {
            w.setProjectId(personalProjectId);
            workflowRepository.save(w);
            log.info("Backfilled workflow '{}' to personal project", w.getName());
        });

        // Backfill credentials without a projectId
        credentialRepository.findByProjectIdIsNull().forEach(c -> {
            c.setProjectId(personalProjectId);
            credentialRepository.save(c);
            log.info("Backfilled credential '{}' to personal project", c.getName());
        });

        // Create or find the Application-Instance project for system-level resources
        String appProjectId = findOrCreateAppProject(owner.getId());

        // Seed default AI agent if none exists
        seedDefaultAgent(appProjectId);

        // Refresh context path filter cache after seeding
        contextPathFilter.refreshCache();
    }

    private String findOrCreateAppProject(String ownerId) {
        // Look for existing Application-Instance project
        var existing = projectRepository.findByNameAndType("Application-Instance", ProjectType.TEAM);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        // Create it
        ProjectEntity appProject = ProjectEntity.builder()
                .name("Application-Instance")
                .type(ProjectType.TEAM)
                .description("System-level resources: default agents, shared credentials, and platform configuration")
                .build();
        appProject = projectRepository.save(appProject);

        // Add owner as project admin
        ProjectRelationEntity relation = ProjectRelationEntity.builder()
                .projectId(appProject.getId())
                .userId(ownerId)
                .role(ProjectRelationEntity.ProjectRole.PROJECT_ADMIN)
                .build();
        projectRelationRepository.save(relation);

        log.info("Created Application-Instance project: {}", appProject.getId());
        return appProject.getId();
    }

    @SuppressWarnings("unchecked")
    private void seedDefaultAgent(String projectId) {
        // Only seed if no agents exist yet
        List<WorkflowEntity> existingAgents = workflowRepository.findAll().stream()
                .filter(w -> "AGENT".equals(w.getType()))
                .toList();
        if (!existingAgents.isEmpty()) return;

        String agentNodeId = "agent_main";
        String modelNodeId = "model_chat";
        String memoryNodeId = "memory_buffer";
        String toolsNodeId = "tools_platform";

        List<Map<String, Object>> nodes = List.of(
                Map.of(
                        "id", agentNodeId,
                        "name", "AI Assistant",
                        "type", "aiAgent",
                        "typeVersion", 1,
                        "position", List.of(500, 300),
                        "parameters", Map.of(
                                "systemMessage", """
                                        You are a helpful workflow automation assistant for CWC. You have access to platform tools \
                                        to discover node types, manage workflows, execute them, and push changes to the user's canvas. \
                                        Use cwc_workflow_guide before building workflows to understand the correct node wiring format. \
                                        Be concise and practical.""",
                                "prompt", "={{$json.chatInput}}",
                                "maxIterations", 20
                        )
                ),
                Map.of(
                        "id", modelNodeId,
                        "name", "Chat Model",
                        "type", "ollamaChatModel",
                        "typeVersion", 1,
                        "position", List.of(200, 200),
                        "parameters", Map.of(
                                "model", "llama3",
                                "temperature", 0.7,
                                "timeout", 120
                        ),
                        "credentials", Map.of(
                                "ollamaApi", Map.of()
                        )
                ),
                Map.of(
                        "id", memoryNodeId,
                        "name", "Window Buffer Memory",
                        "type", "windowBufferMemory",
                        "typeVersion", 1,
                        "position", List.of(200, 350),
                        "parameters", Map.of(
                                "sessionId", "default",
                                "contextWindowLength", 20
                        )
                ),
                Map.of(
                        "id", toolsNodeId,
                        "name", "CWC Platform Tools",
                        "type", "cwcPlatformTool",
                        "typeVersion", 1,
                        "position", List.of(200, 500),
                        "parameters", Map.of(
                                "toolSelectionMode", "all"
                        )
                )
        );

        Map<String, Object> connections = Map.of(
                modelNodeId, Map.of(
                        "ai_languageModel", List.of(
                                List.of(Map.of("node", agentNodeId, "type", "ai_languageModel", "index", 0))
                        )
                ),
                memoryNodeId, Map.of(
                        "ai_memory", List.of(
                                List.of(
                                        Map.of("node", agentNodeId, "type", "ai_memory", "index", 0),
                                        Map.of("node", agentNodeId, "type", "ai_memory", "index", 1)
                                )
                        )
                ),
                toolsNodeId, Map.of(
                        "ai_tool", List.of(
                                List.of(
                                        Map.of("node", agentNodeId, "type", "ai_tool", "index", 0),
                                        Map.of("node", agentNodeId, "type", "ai_tool", "index", 2)
                                )
                        )
                )
        );

        WorkflowEntity agent = WorkflowEntity.builder()
                .projectId(projectId)
                .name("Default CWC Assistant")
                .description("AI agent with memory and platform tools. Configure the Chat Model node with your provider credentials.")
                .type("AGENT")
                .icon("\uD83E\uDD16")
                .nodes(nodes)
                .connections(connections)
                .build();

        workflowRepository.save(agent);
        log.info("Seeded default AI agent: {} ({})", agent.getName(), agent.getId());

        // Set as default agent in AI settings
        AiSettingsDto settings = aiSettingsService.getSettings();
        if (settings.getDefaultAgentId() == null) {
            settings.setDefaultAgentId(agent.getId());
            settings.setEnabled(true);
            aiSettingsService.saveSettings(settings);
            log.info("Set default AI agent to: {}", agent.getId());
        }
    }
}
