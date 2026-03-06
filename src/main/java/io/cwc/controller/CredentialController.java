package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.cwc.credentials.CredentialType;
import io.cwc.credentials.CredentialTypeRegistry;
import io.cwc.dto.CredentialCreateRequest;
import io.cwc.dto.CredentialResponse;
import io.cwc.dto.CredentialTestRequest;
import io.cwc.dto.CredentialTestResult;
import io.cwc.dto.CredentialUpdateRequest;
import io.cwc.dto.ModelInfo;
import io.cwc.exception.NotFoundException;
import io.cwc.service.CredentialService;
import io.cwc.service.ModelListService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;
    private final CredentialTypeRegistry credentialTypeRegistry;
    private final ModelListService modelListService;

    @GetMapping
    public List<CredentialResponse> list(@RequestParam(required = false) String projectId) {
        if (projectId != null) {
            return credentialService.listCredentialsVisibleToProject(projectId);
        }
        return credentialService.listCredentials();
    }

    @GetMapping("/{id}/shares")
    public List<String> getShares(@PathVariable String id) {
        return credentialService.getShareTargetIds(id);
    }

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    public void shareCredential(@PathVariable String id, @RequestBody Map<String, String> body) {
        String targetProjectId = body.get("targetProjectId");
        String callerProjectId = body.get("callerProjectId");
        credentialService.shareCredential(id, targetProjectId, callerProjectId);
    }

    @DeleteMapping("/{id}/shares/{targetProjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unshareCredential(@PathVariable String id, @PathVariable String targetProjectId) {
        credentialService.unshareCredential(id, targetProjectId);
    }

    @GetMapping("/{id}")
    public CredentialResponse get(@PathVariable String id) {
        return credentialService.getCredential(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CredentialResponse create(@RequestBody CredentialCreateRequest request) {
        return credentialService.createCredential(request);
    }

    @PutMapping("/{id}")
    public CredentialResponse update(@PathVariable String id, @RequestBody CredentialUpdateRequest request) {
        return credentialService.updateCredential(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        credentialService.deleteCredential(id);
    }

    @GetMapping("/{id}/data")
    public Map<String, Object> getData(@PathVariable String id) {
        return credentialService.getDecryptedData(id);
    }

    @PostMapping("/test")
    public CredentialTestResult testCredentials(@RequestBody CredentialTestRequest request) {
        return modelListService.testCredentials(request.getType(), request.getData());
    }

    @GetMapping("/{id}/models")
    public List<ModelInfo> listModels(@PathVariable String id,
                                      @RequestParam(required = false) String modelType) {
        CredentialResponse cred = credentialService.getCredential(id);
        Map<String, Object> data = credentialService.getDecryptedData(id);
        return modelListService.listModels(cred.getType(), data, modelType);
    }

    @GetMapping("/types")
    public List<CredentialType> listTypes() {
        return credentialTypeRegistry.getAllTypes().stream()
                .sorted(java.util.Comparator.comparing(CredentialType::getDisplayName))
                .toList();
    }

    @GetMapping("/schema/{type}")
    public CredentialType getSchema(@PathVariable String type) {
        return credentialTypeRegistry.getType(type)
                .orElseThrow(() -> new NotFoundException("Credential type not found: " + type));
    }
}
