package io.trellis.controller;

import io.trellis.config.ProjectContextPathFilter;
import io.trellis.dto.*;
import io.trellis.service.ClusterSyncService;
import io.trellis.service.ProjectService;
import io.trellis.service.TrellisMcpServerManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectContextPathFilter contextPathFilter;
    private final TrellisMcpServerManager mcpServerManager;
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
}
