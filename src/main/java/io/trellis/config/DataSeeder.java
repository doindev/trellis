package io.trellis.config;

import io.trellis.dto.ProjectResponse;
import io.trellis.entity.ProjectEntity;
import io.trellis.entity.ProjectEntity.ProjectType;
import io.trellis.entity.ProjectRelationEntity;
import io.trellis.entity.UserEntity;
import io.trellis.repository.CredentialRepository;
import io.trellis.repository.ProjectRelationRepository;
import io.trellis.repository.ProjectRepository;
import io.trellis.repository.UserRepository;
import io.trellis.repository.WorkflowRepository;
import io.trellis.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
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
                    .email("owner@trellis.local")
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

        // Refresh context path filter cache after seeding
        contextPathFilter.refreshCache();
    }
}
