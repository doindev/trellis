package io.trellis.controller;

import io.trellis.credentials.CredentialType;
import io.trellis.credentials.CredentialTypeRegistry;
import io.trellis.dto.CredentialCreateRequest;
import io.trellis.dto.CredentialResponse;
import io.trellis.dto.CredentialTestRequest;
import io.trellis.dto.CredentialTestResult;
import io.trellis.dto.CredentialUpdateRequest;
import io.trellis.dto.ModelInfo;
import io.trellis.exception.NotFoundException;
import io.trellis.service.CredentialService;
import io.trellis.service.ModelListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
            return credentialService.listCredentialsByProject(projectId);
        }
        return credentialService.listCredentials();
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
