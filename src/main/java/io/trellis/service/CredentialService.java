package io.trellis.service;

import io.trellis.dto.CredentialCreateRequest;
import io.trellis.dto.CredentialResponse;
import io.trellis.dto.CredentialUpdateRequest;
import io.trellis.entity.CredentialEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;
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
        credentialRepository.delete(entity);
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
