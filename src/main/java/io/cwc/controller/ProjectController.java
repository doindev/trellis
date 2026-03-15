package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.cwc.config.ProjectContextPathFilter;
import io.cwc.dto.*;
import io.cwc.service.ClusterSyncService;
import io.cwc.service.McpSettingsService;
import io.cwc.service.ProjectService;
import io.cwc.service.CwcMcpServerManager;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectContextPathFilter contextPathFilter;
    private final CwcMcpServerManager mcpServerManager;
    private final McpSettingsService mcpSettingsService;
    private final ClusterSyncService clusterSyncService;

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
        if (mcpServerManager.isRunning()) {
            mcpServerManager.refreshTools();
        }
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
        return mcpSettingsService.getProjectMcpEndpoints(id);
    }

    @PutMapping("/{id}/mcp")
    public McpEndpointDto updateProjectMcp(@PathVariable String id, @RequestBody ProjectMcpRequest request) {
        ProjectResponse project = projectService.getProject(id);
        McpEndpointDto dto = mcpSettingsService.saveProjectMcpEndpoint(
                id, project.getName(), request.isEnabled(), request.getPath(), request.getTransport());
        if (dto == null) {
            return McpEndpointDto.builder().enabled(false).transport(request.getTransport()).build();
        }
        return dto;
    }
}
