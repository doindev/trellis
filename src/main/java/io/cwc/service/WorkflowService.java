package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.*;
import io.cwc.engine.TriggerSchedulerService;
import io.cwc.entity.ProjectRelationEntity;
import io.cwc.entity.TagEntity;
import io.cwc.entity.UserEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.entity.WorkflowShareEntity;
import io.cwc.entity.WorkflowVersionEntity;
import io.cwc.entity.ProjectEntity.ProjectType;
import io.cwc.entity.ProjectRelationEntity.ProjectRole;
import io.cwc.entity.WorkflowShareEntity.SharePermission;
import io.cwc.exception.BadRequestException;
import io.cwc.exception.ForbiddenException;
import io.cwc.exception.NotFoundException;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeRegistry;
import io.cwc.repository.ProjectRelationRepository;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.TagRepository;
import io.cwc.repository.UserRepository;
import io.cwc.repository.WorkflowRepository;
import io.cwc.repository.AgentShareRepository;
import io.cwc.repository.WorkflowShareRepository;
import io.cwc.repository.WorkflowVersionRepository;
import io.cwc.util.SecurityContextHelper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WebhookService webhookService;
    private final TagRepository tagRepository;
    private final NodeRegistry nodeRegistry;
    private final WorkflowShareRepository workflowShareRepository;
    private final AgentShareRepository agentShareRepository;
    private final ProjectRelationRepository projectRelationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final SecurityContextHelper securityContextHelper;

    @Setter(onMethod_ = {@Autowired, @Lazy})
    private TriggerSchedulerService triggerSchedulerService;

    @Setter(onMethod_ = {@Autowired, @Lazy})
    private ClusterSyncService clusterSyncService;

    // --- Access control helpers ---

    private ProjectRole getProjectRole(String projectId, String userId) {
        if (projectId == null) return null;
        return projectRelationRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectRelationEntity::getRole)
                .orElse(null);
    }

    private void checkAccess(WorkflowEntity workflow) {
        String userId = securityContextHelper.getCurrentUserId();
        // Project member?
        if (workflow.getProjectId() != null
                && projectRelationRepository.existsByProjectIdAndUserId(workflow.getProjectId(), userId)) {
            return;
        }
        // Direct share?
        if (workflowShareRepository.existsByWorkflowIdAndUserId(workflow.getId(), userId)) {
            return;
        }
        throw new ForbiddenException("You do not have access to this workflow");
    }

    private void checkEditAccess(WorkflowEntity workflow) {
        String userId = securityContextHelper.getCurrentUserId();
        // Project owner/admin/editor?
        ProjectRole role = getProjectRole(workflow.getProjectId(), userId);
        if (role == ProjectRole.PROJECT_PERSONAL_OWNER || role == ProjectRole.PROJECT_ADMIN || role == ProjectRole.PROJECT_EDITOR) {
            return;
        }
        // Direct EDIT share?
        var share = workflowShareRepository.findByWorkflowIdAndUserId(workflow.getId(), userId);
        if (share.isPresent() && share.get().getPermission() == SharePermission.EDIT) {
            return;
        }
        throw new ForbiddenException("You do not have edit access to this workflow");
    }

    private void checkPublishAccess(WorkflowEntity workflow) {
        String userId = securityContextHelper.getCurrentUserId();
        ProjectRole role = getProjectRole(workflow.getProjectId(), userId);
        if (role == ProjectRole.PROJECT_PERSONAL_OWNER || role == ProjectRole.PROJECT_ADMIN || role == ProjectRole.PROJECT_EDITOR) {
            return;
        }
        throw new ForbiddenException("You do not have permission to publish this workflow");
    }

    private void checkNotPersonalProject(WorkflowEntity workflow) {
        if (workflow.getProjectId() != null) {
            projectRepository.findById(workflow.getProjectId()).ifPresent(project -> {
                if (project.getType() == ProjectType.PERSONAL) {
                    throw new BadRequestException("Workflows in personal projects cannot be published");
                }
            });
        }
    }

    // --- Core workflow methods ---

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream()
                .filter(w -> !w.isArchived())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listWorkflowsByProject(String projectId) {
        return workflowRepository.findByProjectId(projectId).stream()
                .filter(w -> !w.isArchived())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        checkAccess(entity);
        return toResponse(entity);
    }

    @Transactional
    public WorkflowResponse createWorkflow(WorkflowCreateRequest request) {
        String projectId = request.getProjectId();
        if (projectId == null || projectId.isBlank()) {
            projectId = resolvePersonalProjectId();
        }

        var builder = WorkflowEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .projectId(projectId)
                .nodes(ensureNodeIds(request.getNodes()))
                .connections(request.getConnections())
                .settings(request.getSettings());
        if (request.getType() != null) builder.type(request.getType());
        if (request.getIcon() != null) builder.icon(request.getIcon());
        return toResponse(workflowRepository.save(builder.build()));
    }

    private String resolvePersonalProjectId() {
        String userId = securityContextHelper.getCurrentUserId();
        return projectRelationRepository.findByUserId(userId).stream()
                .filter(r -> r.getRole() == ProjectRole.PROJECT_PERSONAL_OWNER)
                .map(ProjectRelationEntity::getProjectId)
                .findFirst()
                .orElse(null);
    }

    private static final Duration SAVE_REVISION_THROTTLE = Duration.ofSeconds(30);

    @Transactional
    public WorkflowResponse updateWorkflow(String id, WorkflowUpdateRequest request) {
        WorkflowEntity entity = findById(id);
        checkEditAccess(entity);
        boolean dataChanged = request.getNodes() != null || request.getConnections() != null;

        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getType() != null) entity.setType(request.getType());
        if (request.getIcon() != null) entity.setIcon(request.getIcon());
        if (request.getNodes() != null) entity.setNodes(ensureNodeIds(request.getNodes()));
        if (request.getConnections() != null) entity.setConnections(request.getConnections());
        if (request.getSettings() != null) entity.setSettings(request.getSettings());
        if (request.getStaticData() != null) entity.setStaticData(request.getStaticData());
        if (request.getPinData() != null) entity.setPinData(request.getPinData());
        if (request.getMcpEnabled() != null) entity.setMcpEnabled(request.getMcpEnabled());
        if (request.getMcpDescription() != null) entity.setMcpDescription(request.getMcpDescription());
        if (request.getMcpInputSchema() != null) entity.setMcpInputSchema(request.getMcpInputSchema());
        if (request.getMcpOutputSchema() != null) entity.setMcpOutputSchema(request.getMcpOutputSchema());
        if (request.getSwaggerEnabled() != null) entity.setSwaggerEnabled(request.getSwaggerEnabled());

        if (entity.isPublished() && (request.getNodes() != null || request.getConnections() != null
                || request.getMcpInputSchema() != null || request.getMcpOutputSchema() != null)) {
            entity.setVersionIsDirty(true);
        }

        WorkflowEntity saved = workflowRepository.save(entity);

        // Create throttled save revision when nodes or connections change
        if (dataChanged) {
            createThrottledSaveRevision(saved);
        }

        return toResponse(saved);
    }

    private void createThrottledSaveRevision(WorkflowEntity entity) {
        var lastSave = workflowVersionRepository.findFirstByWorkflowIdAndPublishedFalseOrderByPublishedAtDesc(entity.getId());
        if (lastSave.isPresent()) {
            Duration elapsed = Duration.between(lastSave.get().getPublishedAt(), Instant.now());
            if (elapsed.compareTo(SAVE_REVISION_THROTTLE) < 0) {
                return; // too recent, skip
            }
        }

        WorkflowVersionEntity saveVersion = WorkflowVersionEntity.builder()
                .workflowId(entity.getId())
                .versionNumber(0)
                .versionName(null)
                .published(false)
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .publishedAt(Instant.now())
                .build();
        workflowVersionRepository.save(saveVersion);
    }

    @Transactional
    public void deleteWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        checkEditAccess(entity);
        if (entity.isPublished()) {
            webhookService.deregisterWorkflowWebhooks(id);
            triggerSchedulerService.deregisterWorkflowTriggers(id);
        }
        workflowShareRepository.deleteByWorkflowId(id);
        agentShareRepository.deleteByAgentId(id);
        workflowVersionRepository.deleteByWorkflowId(id);
        workflowRepository.delete(entity);
    }

    @Transactional
    public WorkflowResponse publishWorkflow(String id, PublishWorkflowRequest request) {
        WorkflowEntity entity = findById(id);
        checkNotPersonalProject(entity);
        checkPublishAccess(entity);
        validateNodesForPublish(entity);
        int newVersion = entity.getCurrentVersion() + 1;

        String versionName = request.getVersionName();
        if (versionName == null || versionName.isBlank()) {
            versionName = "Version " + newVersion;
        }

        WorkflowVersionEntity.WorkflowVersionEntityBuilder versionBuilder = WorkflowVersionEntity.builder()
                .workflowId(id)
                .versionNumber(newVersion)
                .versionName(versionName)
                .description(request.getDescription())
                .published(true)
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .publishedAt(Instant.now());

        if (request.isIncludePinData() && entity.getPinData() != null) {
            versionBuilder.pinData(entity.getPinData());
        }

        workflowVersionRepository.save(versionBuilder.build());

        entity.setCurrentVersion(newVersion);
        entity.setPublished(true);
        entity.setVersionIsDirty(false);
        entity = workflowRepository.save(entity);

        webhookService.registerWorkflowWebhooks(entity);
        triggerSchedulerService.registerWorkflowTriggers(entity);
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_TRIGGERS);
        log.info("Published workflow: {} ({}) as {}", entity.getName(), id, versionName);

        return toResponse(entity);
    }

    public List<WorkflowVersionResponse> getWorkflowVersions(String id) {
        findById(id); // verify workflow exists
        return workflowVersionRepository.findByWorkflowIdOrderByVersionNumberDesc(id).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkflowVersionResponse> getWorkflowVersionsPaged(String id, int page, int size, String filter) {
        findById(id);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<WorkflowVersionEntity> result;
        if ("published".equals(filter)) {
            result = workflowVersionRepository.findByWorkflowIdAndPublishedOrderByPublishedAtDesc(id, true, pageRequest);
        } else if ("saves".equals(filter)) {
            result = workflowVersionRepository.findByWorkflowIdAndPublishedOrderByPublishedAtDesc(id, false, pageRequest);
        } else {
            result = workflowVersionRepository.findByWorkflowIdOrderByPublishedAtDesc(id, pageRequest);
        }
        return result.map(this::toVersionResponse);
    }

    @Transactional(readOnly = true)
    public WorkflowVersionResponse getWorkflowVersion(String workflowId, String versionId) {
        findById(workflowId);
        WorkflowVersionEntity v = workflowVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (!v.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Version " + versionId + " does not belong to workflow " + workflowId);
        }
        return WorkflowVersionResponse.builder()
                .id(v.getId())
                .workflowId(v.getWorkflowId())
                .versionNumber(v.getVersionNumber())
                .versionName(v.getVersionName())
                .description(v.getDescription())
                .published(v.isPublished())
                .publishedAt(v.getPublishedAt())
                .nodes(v.getNodes())
                .connections(v.getConnections())
                .settings(v.getSettings())
                .build();
    }

    @Transactional
    public WorkflowResponse publishFromVersion(String workflowId, String versionId) {
        WorkflowEntity entity = findById(workflowId);
        checkNotPersonalProject(entity);
        WorkflowVersionEntity version = workflowVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (!version.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Version " + versionId + " does not belong to workflow " + workflowId);
        }

        // Update workflow entity with version's data
        entity.setNodes(version.getNodes());
        entity.setConnections(version.getConnections());
        if (version.getSettings() != null) {
            entity.setSettings(version.getSettings());
        }

        // Validate and publish
        validateNodesForPublish(entity);
        int newVersion = entity.getCurrentVersion() + 1;
        String versionName = "Version " + newVersion;

        WorkflowVersionEntity newVersionEntity = WorkflowVersionEntity.builder()
                .workflowId(workflowId)
                .versionNumber(newVersion)
                .versionName(versionName)
                .description("Published from version " + (version.isPublished() ? version.getVersionName() : "save revision"))
                .published(true)
                .nodes(version.getNodes())
                .connections(version.getConnections())
                .settings(version.getSettings())
                .publishedAt(Instant.now())
                .build();
        workflowVersionRepository.save(newVersionEntity);

        entity.setCurrentVersion(newVersion);
        entity.setPublished(true);
        entity.setVersionIsDirty(false);
        entity = workflowRepository.save(entity);

        webhookService.registerWorkflowWebhooks(entity);
        triggerSchedulerService.registerWorkflowTriggers(entity);
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_TRIGGERS);
        log.info("Published workflow {} ({}) from version {} as {}", entity.getName(), workflowId, versionId, versionName);
        return toResponse(entity);
    }

    @Transactional
    public WorkflowResponse cloneFromVersion(String workflowId, String versionId) {
        WorkflowEntity original = findById(workflowId);
        WorkflowVersionEntity version = workflowVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (!version.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Version " + versionId + " does not belong to workflow " + workflowId);
        }

        WorkflowEntity clone = WorkflowEntity.builder()
                .name("Copy of " + original.getName())
                .description(original.getDescription())
                .projectId(original.getProjectId())
                .nodes(version.getNodes())
                .connections(version.getConnections())
                .settings(version.getSettings())
                .build();
        return toResponse(workflowRepository.save(clone));
    }

    @Transactional
    public WorkflowResponse duplicateWorkflow(String id) {
        WorkflowEntity original = findById(id);
        WorkflowEntity duplicate = WorkflowEntity.builder()
                .name("Copy of " + original.getName())
                .description(original.getDescription())
                .projectId(original.getProjectId())
                .nodes(original.getNodes())
                .connections(original.getConnections())
                .settings(original.getSettings())
                .build();
        return toResponse(workflowRepository.save(duplicate));
    }

    @Transactional
    public WorkflowResponse unpublishWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        if (entity.isPublished()) {
            webhookService.deregisterWorkflowWebhooks(id);
            triggerSchedulerService.deregisterWorkflowTriggers(id);
            clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_TRIGGERS);
            entity.setPublished(false);
            disableSwaggerAccess(entity);
            entity = workflowRepository.save(entity);
            log.info("Unpublished workflow: {} ({})", entity.getName(), id);
        }
        return toResponse(entity);
    }

    @Transactional
    public WorkflowResponse archiveWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        if (entity.isPublished()) {
            webhookService.deregisterWorkflowWebhooks(id);
            triggerSchedulerService.deregisterWorkflowTriggers(id);
            entity.setPublished(false);
        }
        disableSwaggerAccess(entity);
        entity.setArchived(true);
        entity = workflowRepository.save(entity);
        log.info("Archived workflow: {} ({})", entity.getName(), id);
        return toResponse(entity);
    }

    @Transactional
    public void updateStaticData(String id, Object staticData) {
        WorkflowEntity entity = findById(id);
        entity.setStaticData(staticData);
        workflowRepository.save(entity);
    }

    @Transactional
    public WorkflowResponse updateWorkflowTags(String id, List<String> tagIds) {
        WorkflowEntity entity = findById(id);
        Set<TagEntity> tags = new LinkedHashSet<>();
        for (String tagId : tagIds) {
            tags.add(tagRepository.findById(tagId)
                    .orElseThrow(() -> new NotFoundException("Tag not found: " + tagId)));
        }
        entity.setTags(tags);
        return toResponse(workflowRepository.save(entity));
    }

    // --- Move workflow ---

    @Transactional
    public WorkflowResponse moveWorkflow(String id, WorkflowMoveRequest request) {
        WorkflowEntity entity = findById(id);
        checkEditAccess(entity);

        String targetProjectId = request.getProjectId();
        if (targetProjectId == null || targetProjectId.isBlank()) {
            throw new BadRequestException("Target project ID is required");
        }

        String userId = securityContextHelper.getCurrentUserId();
        if (!projectRelationRepository.existsByProjectIdAndUserId(targetProjectId, userId)) {
            throw new ForbiddenException("You do not have access to the target project");
        }

        entity.setProjectId(targetProjectId);
        return toResponse(workflowRepository.save(entity));
    }

    // --- Share methods ---

    @Transactional(readOnly = true)
    public List<WorkflowShareResponse> getShares(String workflowId) {
        WorkflowEntity entity = findById(workflowId);
        checkAccess(entity);

        return workflowShareRepository.findByWorkflowId(workflowId).stream()
                .map(share -> {
                    UserEntity user = userRepository.findById(share.getUserId()).orElse(null);
                    return WorkflowShareResponse.builder()
                            .id(share.getId())
                            .workflowId(share.getWorkflowId())
                            .userId(share.getUserId())
                            .email(user != null ? user.getEmail() : null)
                            .firstName(user != null ? user.getFirstName() : null)
                            .lastName(user != null ? user.getLastName() : null)
                            .permission(share.getPermission().name())
                            .createdAt(share.getCreatedAt())
                            .build();
                })
                .toList();
    }

    @Transactional
    public WorkflowShareResponse addShare(String workflowId, WorkflowShareRequest request) {
        WorkflowEntity entity = findById(workflowId);
        checkEditAccess(entity);

        if (entity.isPublished()) {
            throw new BadRequestException("Published workflows cannot be shared");
        }

        UserEntity targetUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.getUserId()));

        if (workflowShareRepository.existsByWorkflowIdAndUserId(workflowId, request.getUserId())) {
            throw new BadRequestException("Workflow is already shared with this user");
        }

        SharePermission permission = SharePermission.valueOf(request.getPermission().toUpperCase());

        WorkflowShareEntity share = WorkflowShareEntity.builder()
                .workflowId(workflowId)
                .userId(request.getUserId())
                .permission(permission)
                .build();
        share = workflowShareRepository.save(share);

        return WorkflowShareResponse.builder()
                .id(share.getId())
                .workflowId(share.getWorkflowId())
                .userId(share.getUserId())
                .email(targetUser.getEmail())
                .firstName(targetUser.getFirstName())
                .lastName(targetUser.getLastName())
                .permission(share.getPermission().name())
                .createdAt(share.getCreatedAt())
                .build();
    }

    @Transactional
    public WorkflowShareResponse updateShare(String workflowId, String shareId, WorkflowShareRequest request) {
        WorkflowEntity entity = findById(workflowId);
        checkEditAccess(entity);

        WorkflowShareEntity share = workflowShareRepository.findById(shareId)
                .orElseThrow(() -> new NotFoundException("Share not found: " + shareId));
        if (!share.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Share " + shareId + " does not belong to workflow " + workflowId);
        }

        SharePermission permission = SharePermission.valueOf(request.getPermission().toUpperCase());
        share.setPermission(permission);
        share = workflowShareRepository.save(share);

        UserEntity user = userRepository.findById(share.getUserId()).orElse(null);
        return WorkflowShareResponse.builder()
                .id(share.getId())
                .workflowId(share.getWorkflowId())
                .userId(share.getUserId())
                .email(user != null ? user.getEmail() : null)
                .firstName(user != null ? user.getFirstName() : null)
                .lastName(user != null ? user.getLastName() : null)
                .permission(share.getPermission().name())
                .createdAt(share.getCreatedAt())
                .build();
    }

    @Transactional
    public void removeShare(String workflowId, String shareId) {
        WorkflowEntity entity = findById(workflowId);
        checkEditAccess(entity);

        WorkflowShareEntity share = workflowShareRepository.findById(shareId)
                .orElseThrow(() -> new NotFoundException("Share not found: " + shareId));
        if (!share.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Share " + shareId + " does not belong to workflow " + workflowId);
        }
        workflowShareRepository.delete(share);
    }

    // --- Agent share methods ---

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listAgentsVisibleToProject(String projectId) {
        // Agents owned by the project
        List<WorkflowEntity> owned = workflowRepository.findByProjectIdAndType(projectId, "AGENT").stream()
                .filter(w -> !w.isArchived())
                .toList();
        // Agents shared with the project
        List<String> sharedAgentIds = agentShareRepository.findByTargetProjectId(projectId).stream()
                .map(io.cwc.entity.AgentShareEntity::getAgentId)
                .toList();
        List<WorkflowEntity> shared = sharedAgentIds.stream()
                .map(id -> workflowRepository.findById(id).orElse(null))
                .filter(w -> w != null && !w.isArchived())
                .toList();
        // Deduplicate
        Set<String> seen = new LinkedHashSet<>();
        List<WorkflowResponse> result = new ArrayList<>();
        for (WorkflowEntity w : owned) {
            if (seen.add(w.getId())) result.add(toResponse(w));
        }
        for (WorkflowEntity w : shared) {
            if (seen.add(w.getId())) result.add(toResponse(w));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> getAgentProjectShares(String agentId) {
        return agentShareRepository.findByAgentId(agentId).stream()
                .map(share -> {
                    String projectName = projectRepository.findById(share.getTargetProjectId())
                            .map(p -> p.getName())
                            .orElse(share.getTargetProjectId());
                    return Map.of(
                            "id", share.getId(),
                            "targetProjectId", share.getTargetProjectId(),
                            "projectName", projectName
                    );
                })
                .toList();
    }

    @Transactional
    public Map<String, String> shareAgentWithProject(String agentId, String targetProjectId) {
        findById(agentId); // verify exists
        io.cwc.entity.AgentShareEntity share = io.cwc.entity.AgentShareEntity.builder()
                .agentId(agentId)
                .targetProjectId(targetProjectId)
                .build();
        share = agentShareRepository.save(share);
        return Map.of("id", share.getId(), "agentId", agentId, "targetProjectId", targetProjectId);
    }

    @Transactional
    public void unshareAgentFromProject(String agentId, String targetProjectId) {
        agentShareRepository.deleteByAgentIdAndTargetProjectId(agentId, targetProjectId);
    }

    // --- Helpers ---

    /**
     * Ensures every node in the list has an "id" field. If a node is missing "id",
     * its "name" is used as the id (matching name-based connection references).
     */
    @SuppressWarnings("unchecked")
    private Object ensureNodeIds(Object nodesObj) {
        if (!(nodesObj instanceof List<?> nodeList)) return nodesObj;
        for (Object item : nodeList) {
            if (item instanceof Map<?, ?> nodeMap) {
                Map<String, Object> mutable = (Map<String, Object>) nodeMap;
                if (mutable.get("id") == null || mutable.get("id").toString().isBlank()) {
                    String name = (String) mutable.get("name");
                    if (name != null) {
                        mutable.put("id", name);
                    }
                }
            }
        }
        return nodesObj;
    }

    private void disableSwaggerAccess(WorkflowEntity entity) {
        if (entity.isSwaggerEnabled()) {
            entity.setSwaggerEnabled(false);
            tagRepository.findByName("swagger").ifPresent(tag -> entity.getTags().remove(tag));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNodesForPublish(WorkflowEntity entity) {
        Object nodesObj = entity.getNodes();
        if (!(nodesObj instanceof List<?> nodeList) || nodeList.isEmpty()) return;

        List<String> errors = new ArrayList<>();
        for (Object item : nodeList) {
            if (!(item instanceof Map<?, ?> nodeMap)) continue;
            String nodeType = (String) nodeMap.get("type");
            String nodeName = (String) nodeMap.get("name");
            if (nodeType == null) continue;

            var reg = nodeRegistry.getNode(nodeType).orElse(null);
            if (reg == null) continue;

            // Check credentials
            Map<String, Object> credentials = (Map<String, Object>) nodeMap.get("credentials");
            for (String credType : reg.getCredentials()) {
                if (credentials == null || !credentials.containsKey(credType)
                        || credentials.get(credType) == null || credentials.get(credType).toString().isBlank()) {
                    errors.add(nodeName + ": missing credential '" + credType + "'");
                }
            }

            // Check required parameters
            Map<String, Object> params = (Map<String, Object>) nodeMap.get("parameters");
            if (params == null) params = Map.of();
            for (NodeParameter param : reg.getParameters()) {
                if (!param.isRequired()) continue;
                if (param.getDefaultValue() != null && !"".equals(param.getDefaultValue())) continue;

                // Check displayOptions.show conditions
                if (param.getDisplayOptions() != null) {
                    Object showObj = param.getDisplayOptions().get("show");
                    if (showObj instanceof Map<?, ?> showMap) {
                        boolean conditionsMet = true;
                        for (Map.Entry<?, ?> entry : showMap.entrySet()) {
                            Object currentVal = params.get(entry.getKey());
                            Object allowed = entry.getValue();
                            if (allowed instanceof List<?> allowedList) {
                                if (!allowedList.contains(currentVal)) {
                                    conditionsMet = false;
                                    break;
                                }
                            } else {
                                if (!java.util.Objects.equals(currentVal, allowed)) {
                                    conditionsMet = false;
                                    break;
                                }
                            }
                        }
                        if (!conditionsMet) continue;
                    }
                }

                Object value = params.get(param.getName());
                if (value == null || value.toString().isBlank()) {
                    errors.add(nodeName + ": missing required parameter '" + param.getDisplayName() + "'");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException("Workflow has validation errors: " + String.join("; ", errors));
        }
    }

    private WorkflowVersionResponse toVersionResponse(WorkflowVersionEntity v) {
        return WorkflowVersionResponse.builder()
                .id(v.getId())
                .workflowId(v.getWorkflowId())
                .versionNumber(v.getVersionNumber())
                .versionName(v.getVersionName())
                .description(v.getDescription())
                .published(v.isPublished())
                .publishedAt(v.getPublishedAt())
                .build();
    }

    public WorkflowEntity findById(String id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + id));
    }

    private WorkflowResponse toResponse(WorkflowEntity entity) {
        List<TagResponse> tagResponses = entity.getTags() != null
                ? entity.getTags().stream()
                    .map(t -> TagResponse.builder()
                            .id(t.getId())
                            .name(t.getName())
                            .createdAt(t.getCreatedAt())
                            .updatedAt(t.getUpdatedAt())
                            .build())
                    .toList()
                : List.of();

        return WorkflowResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .description(entity.getDescription())
                .type(entity.getType())
                .icon(entity.getIcon())
                .published(entity.isPublished())
                .archived(entity.isArchived())
                .currentVersion(entity.getCurrentVersion())
                .versionIsDirty(entity.isVersionIsDirty())
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .staticData(entity.getStaticData())
                .pinData(entity.getPinData())
                .mcpEnabled(entity.isMcpEnabled())
                .mcpDescription(entity.getMcpDescription())
                .mcpInputSchema(entity.getMcpInputSchema())
                .mcpOutputSchema(entity.getMcpOutputSchema())
                .swaggerEnabled(entity.isSwaggerEnabled())
                .tags(tagResponses)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
