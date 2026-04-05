package io.cwc.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.cwc.entity.EnvironmentEntity;
import io.cwc.entity.SourceControlSettingsEntity;
import io.cwc.exception.BadRequestException;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.EnvironmentRepository;
import io.cwc.repository.SourceControlSettingsRepository;
import io.cwc.service.CredentialEncryptionService;
import io.cwc.service.GitSyncProvider;
import java.util.Optional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Environments and source control settings endpoints.
 * Populates the previously stubbed "Environments" section in Settings.
 */
@RestController
@RequestMapping("/api/settings/environments")
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentRepository environmentRepository;
    private final SourceControlSettingsRepository sourceControlRepository;
    private final CredentialEncryptionService encryptionService;
    private final Optional<GitSyncProvider> gitSyncProvider;

    // ---- Environment CRUD ----

    @GetMapping
    public List<EnvironmentEntity> listEnvironments() {
        return environmentRepository.findAllByOrderBySortOrderAsc();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentEntity createEnvironment(@RequestBody EnvironmentRequest request) {
        if (environmentRepository.findByName(request.getName()).isPresent()) {
            throw new BadRequestException("Environment with name '" + request.getName() + "' already exists");
        }
        EnvironmentEntity entity = EnvironmentEntity.builder()
                .name(request.getName())
                .gitBranch(request.getGitBranch())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder())
                .build();
        return environmentRepository.save(entity);
    }

    @PutMapping("/{id}")
    public EnvironmentEntity updateEnvironment(@PathVariable String id, @RequestBody EnvironmentRequest request) {
        EnvironmentEntity entity = environmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + id));
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getGitBranch() != null) entity.setGitBranch(request.getGitBranch());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        entity.setSortOrder(request.getSortOrder());
        return environmentRepository.save(entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEnvironment(@PathVariable String id) {
        EnvironmentEntity entity = environmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Environment not found: " + id));
        environmentRepository.delete(entity);
    }

    // ---- Source Control Settings ----

    @GetMapping("/source-control")
    public Map<String, Object> getSourceControlSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        var entity = sourceControlRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (entity == null) {
            result.put("connected", false);
            return result;
        }
        result.put("connected", entity.isEnabled());
        result.put("provider", entity.getProvider());
        result.put("repoUrl", entity.getRepoUrl());
        result.put("branch", entity.getBranch());
        result.put("lastSyncAt", entity.getLastSyncAt());
        result.put("lastSyncStatus", entity.getLastSyncStatus());
        result.put("lastSyncError", entity.getLastSyncError());
        return result;
    }

    @PutMapping("/source-control")
    public Map<String, Object> updateSourceControlSettings(@RequestBody SourceControlRequest request) {
        SourceControlSettingsEntity entity = sourceControlRepository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(SourceControlSettingsEntity::new);

        if (request.getProvider() != null) entity.setProvider(request.getProvider());
        if (request.getRepoUrl() != null) entity.setRepoUrl(request.getRepoUrl());
        if (request.getBranch() != null) entity.setBranch(request.getBranch());
        if (request.getToken() != null && !request.getToken().isBlank()) {
            entity.setToken(encryptionService.encryptString(request.getToken()));
        }
        entity.setEnabled(request.isEnabled());

        sourceControlRepository.save(entity);
        return getSourceControlSettings();
    }

    @PostMapping("/sync")
    public Map<String, Object> triggerSync() {
        var entity = sourceControlRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        boolean success = gitSyncProvider.orElseThrow(() -> new io.cwc.exception.ServiceUnavailableException("Git module not available")).sync();

        if (entity != null) {
            entity.setLastSyncAt(Instant.now());
            entity.setLastSyncStatus(success ? "SUCCESS" : "FAILED");
            if (!success) entity.setLastSyncError("Git sync failed — check server logs for details");
            else entity.setLastSyncError(null);
            sourceControlRepository.save(entity);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("syncedAt", Instant.now());
        return result;
    }

    @Data
    public static class EnvironmentRequest {
        private String name;
        private String gitBranch;
        private String description;
        private int sortOrder;
    }

    @Data
    public static class SourceControlRequest {
        private String provider;
        private String repoUrl;
        private String branch;
        private String token;
        private boolean enabled;
    }
}
