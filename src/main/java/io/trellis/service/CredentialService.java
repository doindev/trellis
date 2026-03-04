package io.trellis.service;

import io.trellis.dto.CredentialCreateRequest;
import io.trellis.dto.CredentialResponse;
import io.trellis.dto.CredentialUpdateRequest;
import io.trellis.entity.CredentialEntity;
import io.trellis.entity.CredentialShareEntity;
import io.trellis.exception.BadRequestException;
import io.trellis.exception.ForbiddenException;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.CredentialRepository;
import io.trellis.repository.CredentialShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final CredentialShareRepository credentialShareRepository;
    private final CredentialEncryptionService encryptionService;

    public List<CredentialResponse> listCredentials() {
        return credentialRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CredentialResponse> listCredentialsByProject(String projectId) {
        return credentialRepository.findByProjectId(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns credentials visible to a project: owned by the project,
     * shared with the project, or global (projectId is null).
     */
    public List<CredentialResponse> listCredentialsVisibleToProject(String projectId) {
        Set<String> seen = new LinkedHashSet<>();
        List<CredentialResponse> result = new ArrayList<>();

        // 1. Credentials owned by this project
        for (CredentialEntity entity : credentialRepository.findByProjectId(projectId)) {
            if (seen.add(entity.getId())) {
                CredentialResponse resp = toResponse(entity);
                resp.setSharedWithProjectIds(getShareTargetIds(entity.getId()));
                result.add(resp);
            }
        }

        // 2. Credentials shared with this project
        List<CredentialShareEntity> shares = credentialShareRepository.findByTargetProjectId(projectId);
        for (CredentialShareEntity share : shares) {
            if (seen.add(share.getCredentialId())) {
                credentialRepository.findById(share.getCredentialId())
                        .ifPresent(entity -> result.add(toResponse(entity)));
            }
        }

        // 3. Global credentials (no project)
        for (CredentialEntity entity : credentialRepository.findByProjectIdIsNull()) {
            if (seen.add(entity.getId())) {
                result.add(toResponse(entity));
            }
        }

        return result;
    }

    public List<CredentialResponse> listCredentialsByType(String type) {
        return credentialRepository.findByType(type).stream()
                .map(this::toResponse)
                .toList();
    }

    public CredentialResponse getCredential(String id) {
        return toResponse(findById(id));
    }

    public Map<String, Object> getDecryptedData(String id) {
        CredentialEntity entity = findById(id);
        return encryptionService.decrypt(entity.getData());
    }

    @Transactional
    public CredentialResponse createCredential(CredentialCreateRequest request) {
        CredentialEntity entity = CredentialEntity.builder()
                .name(request.getName())
                .type(request.getType())
                .projectId(request.getProjectId())
                .data(encryptionService.encrypt(request.getData()))
                .build();
        return toResponse(credentialRepository.save(entity));
    }

    @Transactional
    public CredentialResponse updateCredential(String id, CredentialUpdateRequest request) {
        CredentialEntity entity = findById(id);
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getData() != null) entity.setData(encryptionService.encrypt(request.getData()));
        return toResponse(credentialRepository.save(entity));
    }

    @Transactional
    public void deleteCredential(String id) {
        CredentialEntity entity = findById(id);
        List<String> shares = getShareTargetIds(id);
        if (!shares.isEmpty()) {
            throw new BadRequestException(
                "Cannot delete credential '" + entity.getName()
                + "' because it is shared with other projects. Revoke all shares first.");
        }
        credentialRepository.delete(entity);
    }

    // --- Sharing ---

    @Transactional
    public CredentialShareEntity shareCredential(String credentialId, String targetProjectId, String callerProjectId) {
        CredentialEntity entity = findById(credentialId);
        if (entity.getProjectId() == null || !entity.getProjectId().equals(callerProjectId)) {
            throw new ForbiddenException("Only the owning project can share a credential");
        }
        if (entity.getProjectId().equals(targetProjectId)) {
            throw new BadRequestException("Cannot share a credential with its own project");
        }
        CredentialShareEntity share = CredentialShareEntity.builder()
                .credentialId(credentialId)
                .targetProjectId(targetProjectId)
                .build();
        return credentialShareRepository.save(share);
    }

    @Transactional
    public void unshareCredential(String credentialId, String targetProjectId) {
        credentialShareRepository.deleteByCredentialIdAndTargetProjectId(credentialId, targetProjectId);
    }

    public List<String> getShareTargetIds(String credentialId) {
        return credentialShareRepository.findByCredentialId(credentialId).stream()
                .map(CredentialShareEntity::getTargetProjectId)
                .toList();
    }

    private CredentialEntity findById(String id) {
        return credentialRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Credential not found: " + id));
    }

    private CredentialResponse toResponse(CredentialEntity entity) {
        return CredentialResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
