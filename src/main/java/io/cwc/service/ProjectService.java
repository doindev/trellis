package io.cwc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.dto.*;
import io.cwc.entity.*;
import io.cwc.entity.ProjectEntity.ProjectType;
import io.cwc.entity.ProjectRelationEntity.ProjectRole;
import io.cwc.exception.BadRequestException;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.*;
import io.cwc.util.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final Pattern CONTEXT_PATH_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final Set<String> RESERVED_CONTEXT_PATHS = Set.of(
            "api", "webhook", "webhook-test", "h2-console", "home", "workflow",
            "projects", "settings", "insights", "executions", "credentials",
            "variables", "templates"
    );

    private final ProjectRepository projectRepository;
    private final ProjectRelationRepository projectRelationRepository;
    private final WorkflowRepository workflowRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final WebhookService webhookService;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    public List<ProjectResponse> listProjects() {
        return projectRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public ProjectResponse getProject(String id) {
        return toDetailResponse(findById(id));
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
        if (request.getContextPath() != null && !request.getContextPath().isBlank()) {
            validateContextPath(request.getContextPath(), null);
        }
        ProjectEntity entity = ProjectEntity.builder()
                .name(request.getName())
                .type(ProjectType.TEAM)
                .icon(serializeIcon(request.getIcon()))
                .description(request.getDescription())
                .contextPath(request.getContextPath() != null && !request.getContextPath().isBlank()
                        ? request.getContextPath() : null)
                .build();
        entity = projectRepository.save(entity);

        // Add the creator as project admin
        String userId = securityContextHelper.getCurrentUserId();
        ProjectRelationEntity relation = ProjectRelationEntity.builder()
                .projectId(entity.getId())
                .userId(userId)
                .role(ProjectRole.PROJECT_ADMIN)
                .build();
        projectRelationRepository.save(relation);

        log.info("Created team project: {} ({})", entity.getName(), entity.getId());
        return toResponse(entity);
    }

    @Transactional
    public ProjectResponse updateProject(String id, ProjectUpdateRequest request) {
        ProjectEntity entity = findById(id);
        if (entity.getType() == ProjectType.PERSONAL) {
            throw new BadRequestException("Personal projects cannot be updated");
        }
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getIcon() != null) entity.setIcon(serializeIcon(request.getIcon()));

        // Handle contextPath update
        if (request.getContextPath() != null) {
            String oldContextPath = entity.getContextPath();
            String newContextPath = request.getContextPath().isBlank() ? null : request.getContextPath();
            if (newContextPath != null) {
                validateContextPath(newContextPath, id);
            }
            entity.setContextPath(newContextPath);
            boolean contextPathChanged = (oldContextPath == null && newContextPath != null)
                    || (oldContextPath != null && !oldContextPath.equals(newContextPath));
            if (contextPathChanged) {
                entity = projectRepository.save(entity);
                // Re-register webhooks for all published workflows in this project
                try {
                    reRegisterProjectWebhooks(id);
                } catch (Exception ex) {
                    log.warn("Failed to re-register webhooks after context path change for project {}: {}", id, ex.getMessage());
                }
                log.info("Updated project: {} ({}) — context path changed to '{}'", entity.getName(), id, newContextPath);
                return toDetailResponse(entity);
            }
        }

        entity = projectRepository.save(entity);
        log.info("Updated project: {} ({})", entity.getName(), id);
        return toDetailResponse(entity);
    }

    private void reRegisterProjectWebhooks(String projectId) {
        List<WorkflowEntity> publishedWorkflows = workflowRepository.findByProjectIdAndPublished(projectId, true);
        for (WorkflowEntity workflow : publishedWorkflows) {
            webhookService.registerWorkflowWebhooks(workflow);
        }
    }

    @Transactional
    public void deleteProject(String id, ProjectDeleteRequest request) {
        ProjectEntity entity = findById(id);
        if (entity.getType() == ProjectType.PERSONAL) {
            throw new BadRequestException("Personal projects cannot be deleted");
        }

        if (request != null && request.getTransferToProjectId() != null) {
            String targetId = request.getTransferToProjectId();
            findById(targetId); // validate target exists
            workflowRepository.findByProjectId(id).forEach(w -> {
                w.setProjectId(targetId);
                workflowRepository.save(w);
            });
            credentialRepository.findByProjectId(id).forEach(c -> {
                c.setProjectId(targetId);
                credentialRepository.save(c);
            });
        } else {
            workflowRepository.findByProjectId(id).forEach(workflowRepository::delete);
            credentialRepository.findByProjectId(id).forEach(credentialRepository::delete);
        }

        projectRelationRepository.findByProjectId(id).forEach(projectRelationRepository::delete);
        projectRepository.delete(entity);
        log.info("Deleted project: {} ({})", entity.getName(), id);
    }

    public List<ProjectMemberResponse> getMembers(String projectId) {
        findById(projectId);
        return projectRelationRepository.findByProjectId(projectId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(String projectId, ProjectMemberRequest request) {
        findById(projectId);
        ProjectRole role = parseRole(request.getRole());
        if (role == ProjectRole.PROJECT_PERSONAL_OWNER) {
            throw new BadRequestException("Cannot assign PROJECT_PERSONAL_OWNER role manually");
        }
        if (projectRelationRepository.existsByProjectIdAndUserId(projectId, request.getUserId())) {
            throw new BadRequestException("User is already a member of this project");
        }
        userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.getUserId()));

        ProjectRelationEntity relation = ProjectRelationEntity.builder()
                .projectId(projectId)
                .userId(request.getUserId())
                .role(role)
                .build();
        relation = projectRelationRepository.save(relation);
        log.info("Added member {} to project {} with role {}", request.getUserId(), projectId, role);
        return toMemberResponse(relation);
    }

    @Transactional
    public ProjectMemberResponse updateMember(String projectId, String userId, ProjectMemberRequest request) {
        findById(projectId);
        ProjectRelationEntity relation = projectRelationRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found in project"));
        ProjectRole newRole = parseRole(request.getRole());
        if (newRole == ProjectRole.PROJECT_PERSONAL_OWNER) {
            throw new BadRequestException("Cannot assign PROJECT_PERSONAL_OWNER role manually");
        }
        if (relation.getRole() == ProjectRole.PROJECT_ADMIN) {
            long adminCount = projectRelationRepository.findByProjectId(projectId).stream()
                    .filter(r -> r.getRole() == ProjectRole.PROJECT_ADMIN)
                    .count();
            if (adminCount <= 1 && newRole != ProjectRole.PROJECT_ADMIN) {
                throw new BadRequestException("Project must have at least one admin");
            }
        }
        relation.setRole(newRole);
        relation = projectRelationRepository.save(relation);
        log.info("Updated member {} role to {} in project {}", userId, newRole, projectId);
        return toMemberResponse(relation);
    }

    @Transactional
    public void removeMember(String projectId, String userId) {
        findById(projectId);
        ProjectRelationEntity relation = projectRelationRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found in project"));
        if (relation.getRole() == ProjectRole.PROJECT_PERSONAL_OWNER) {
            throw new BadRequestException("Cannot remove personal project owner");
        }
        if (relation.getRole() == ProjectRole.PROJECT_ADMIN) {
            long adminCount = projectRelationRepository.findByProjectId(projectId).stream()
                    .filter(r -> r.getRole() == ProjectRole.PROJECT_ADMIN)
                    .count();
            if (adminCount <= 1) {
                throw new BadRequestException("Project must have at least one admin");
            }
        }
        projectRelationRepository.delete(relation);
        log.info("Removed member {} from project {}", userId, projectId);
    }

    @Transactional
    public ProjectResponse createPersonalProject(String userId) {
        ProjectEntity entity = ProjectEntity.builder()
                .name("Personal")
                .type(ProjectType.PERSONAL)
                .contextPath(userId)
                .build();
        entity = projectRepository.save(entity);

        ProjectRelationEntity relation = ProjectRelationEntity.builder()
                .projectId(entity.getId())
                .userId(userId)
                .role(ProjectRole.PROJECT_PERSONAL_OWNER)
                .build();
        projectRelationRepository.save(relation);

        log.info("Created personal project for user {}: {}", userId, entity.getId());
        return toResponse(entity);
    }

    private void validateContextPath(String contextPath, String excludeProjectId) {
        if (!CONTEXT_PATH_PATTERN.matcher(contextPath).matches()) {
            throw new BadRequestException("Context path must contain only lowercase letters, numbers, and hyphens");
        }
        if (RESERVED_CONTEXT_PATHS.contains(contextPath)) {
            throw new BadRequestException("Context path '" + contextPath + "' is reserved");
        }
        if (contextPath.endsWith("-test")) {
            throw new BadRequestException("Context path cannot end with '-test'");
        }
        projectRepository.findByContextPath(contextPath).ifPresent(existing -> {
            if (!existing.getId().equals(excludeProjectId)) {
                throw new BadRequestException("Context path '" + contextPath + "' is already in use");
            }
        });
    }

    public String getUserRoleString(String projectId, String userId) {
        return projectRelationRepository.findByProjectIdAndUserId(projectId, userId)
                .map(r -> r.getRole().name())
                .orElse(null);
    }

    public ProjectEntity findById(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    private ProjectResponse toResponse(ProjectEntity entity) {
        return ProjectResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType().name())
                .icon(parseIcon(entity.getIcon()))
                .description(entity.getDescription())
                .contextPath(entity.getContextPath())
                .workflowCount(workflowRepository.findByProjectId(entity.getId()).size())
                .credentialCount(credentialRepository.findByProjectId(entity.getId()).size())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProjectResponse toDetailResponse(ProjectEntity entity) {
        List<ProjectMemberResponse> members = projectRelationRepository.findByProjectId(entity.getId()).stream()
                .map(this::toMemberResponse)
                .toList();
        return ProjectResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType().name())
                .icon(parseIcon(entity.getIcon()))
                .description(entity.getDescription())
                .contextPath(entity.getContextPath())
                .members(members)
                .workflowCount(workflowRepository.findByProjectId(entity.getId()).size())
                .credentialCount(credentialRepository.findByProjectId(entity.getId()).size())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private ProjectMemberResponse toMemberResponse(ProjectRelationEntity relation) {
        var builder = ProjectMemberResponse.builder()
                .userId(relation.getUserId())
                .role(relation.getRole().name())
                .createdAt(relation.getCreatedAt());
        userRepository.findById(relation.getUserId()).ifPresent(user -> {
            builder.email(user.getEmail());
            builder.firstName(user.getFirstName());
            builder.lastName(user.getLastName());
        });
        return builder.build();
    }

    private Map<String, String> parseIcon(String iconJson) {
        if (iconJson == null || iconJson.isBlank()) return null;
        try {
            return objectMapper.readValue(iconJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String serializeIcon(Map<String, String> icon) {
        if (icon == null) return null;
        try {
            return objectMapper.writeValueAsString(icon);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ProjectRole parseRole(String role) {
        try {
            return ProjectRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + role);
        }
    }
}
