package io.cwc.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.cwc.config.ProjectContextPathFilter;
import io.cwc.dto.ConfigReloadResult;
import io.cwc.dto.GitImportRequest;
import io.cwc.dto.McpEndpointDto;
import io.cwc.dto.ProjectCreateRequest;
import io.cwc.dto.ProjectDeleteRequest;
import io.cwc.dto.ProjectMcpRequest;
import io.cwc.dto.ProjectMemberRequest;
import io.cwc.dto.ProjectMemberResponse;
import io.cwc.dto.ProjectResponse;
import io.cwc.dto.ProjectUpdateRequest;
import io.cwc.entity.ProjectSourceControlEntity;
import io.cwc.service.ClusterSyncService;
import io.cwc.service.CwcMcpServerManager;
import io.cwc.service.McpSettingsService;
import io.cwc.service.ProjectGitProvider;
import io.cwc.service.ProjectService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectContextPathFilter contextPathFilter;
    private final Optional<CwcMcpServerManager> mcpServerManager;
    private final Optional<McpSettingsService> mcpSettingsService;
    private final ClusterSyncService clusterSyncService;
    private final Optional<ProjectGitProvider> projectGitProvider;

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.listProjects();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable String id) {
        return projectService.getProject(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@RequestBody ProjectCreateRequest request) {
        ProjectResponse response = projectService.createProject(request);
        contextPathFilter.refreshCache();
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_CONTEXT_PATHS);
        return response;
    }

    @PatchMapping("/{id}")
    public ProjectResponse update(@PathVariable String id, @RequestBody ProjectUpdateRequest request) {
        ProjectResponse response = projectService.updateProject(id, request);
        contextPathFilter.refreshCache();
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_CONTEXT_PATHS);
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_WEBHOOKS);
        mcpServerManager.filter(CwcMcpServerManager::isRunning)
                .ifPresent(CwcMcpServerManager::refreshTools);
        return response;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestBody(required = false) ProjectDeleteRequest request) {
        projectService.deleteProject(id, request);
        contextPathFilter.refreshCache();
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_CONTEXT_PATHS);
        clusterSyncService.notifyChange(ClusterSyncService.DOMAIN_WEBHOOKS);
    }

    @GetMapping("/{id}/members")
    public List<ProjectMemberResponse> getMembers(@PathVariable String id) {
        return projectService.getMembers(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse addMember(@PathVariable String id, @RequestBody ProjectMemberRequest request) {
        return projectService.addMember(id, request);
    }

    @PatchMapping("/{id}/members/{userId}")
    public ProjectMemberResponse updateMember(@PathVariable String id, @PathVariable String userId, @RequestBody ProjectMemberRequest request) {
        return projectService.updateMember(id, userId, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String id, @PathVariable String userId) {
        projectService.removeMember(id, userId);
    }

    @GetMapping("/{id}/mcp")
    public List<McpEndpointDto> getProjectMcp(@PathVariable String id) {
        return mcpSettingsService.map(s -> s.getProjectMcpEndpoints(id)).orElse(List.of());
    }

    @PutMapping("/{id}/mcp")
    public McpEndpointDto updateProjectMcp(@PathVariable String id, @RequestBody ProjectMcpRequest request) {
        if (mcpSettingsService.isEmpty()) {
            return McpEndpointDto.builder().enabled(false).transport(request.getTransport()).build();
        }
        ProjectResponse project = projectService.getProject(id);
        McpEndpointDto dto = mcpSettingsService.get().saveProjectMcpEndpoint(
                id, project.getName(), request.isEnabled(), request.getPath(), request.getTransport());
        if (dto == null) {
            return McpEndpointDto.builder().enabled(false).transport(request.getTransport()).build();
        }
        return dto;
    }

    // --- Source Control ---

    @GetMapping("/{id}/source-control")
    public Map<String, Object> getSourceControl(@PathVariable String id) {
        ProjectSourceControlEntity sc = projectGitProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).getLink(id);
        if (sc == null) {
            return Map.of("connected", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true);
        result.put("provider", sc.getProvider());
        result.put("repoUrl", sc.getRepoUrl());
        result.put("branch", sc.getBranch());
        result.put("lastSyncAt", sc.getLastSyncAt() != null ? sc.getLastSyncAt().toString() : null);
        result.put("lastSyncStatus", sc.getLastSyncStatus());
        result.put("lastSyncError", sc.getLastSyncError());
        return result;
    }

    @PutMapping("/{id}/source-control")
    public Map<String, Object> updateSourceControl(@PathVariable String id,
                                                    @RequestBody GitImportRequest request) {
        projectGitProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).linkRepo(id, request.getRepoUrl(), request.getBranch(),
                request.getToken(), request.getProvider());
        return getSourceControl(id);
    }

    @DeleteMapping("/{id}/source-control")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSourceControl(@PathVariable String id) {
        projectGitProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).unlinkRepo(id);
    }

    @PostMapping("/{id}/source-control/sync")
    public ConfigReloadResult syncSourceControl(@PathVariable String id) {
        return projectGitProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).syncProject(id);
    }

    @PostMapping("/{id}/source-control/push")
    public Map<String, String> pushSourceControl(@PathVariable String id,
                                                  @RequestBody(required = false) Map<String, String> request) {
        String commitMessage = request != null ? request.get("commitMessage") : null;
        String targetBranch = request != null ? request.get("targetBranch") : null;
        return projectGitProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).pushProject(id, commitMessage, targetBranch);
    }
}
