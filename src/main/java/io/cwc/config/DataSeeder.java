package io.cwc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.cwc.dto.ProjectResponse;
import io.cwc.entity.ProjectEntity;
import io.cwc.entity.ProjectRelationEntity;
import io.cwc.entity.UserEntity;
import io.cwc.entity.ProjectEntity.ProjectType;
import io.cwc.repository.CredentialRepository;
import io.cwc.repository.ProjectRelationRepository;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.UserRepository;
import io.cwc.repository.WorkflowRepository;
import io.cwc.service.ProjectService;

import org.springframework.core.annotation.Order;

import java.util.List;

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
        findOrCreateAppProject(owner.getId());

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
                .configId("application-instance")
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

}
