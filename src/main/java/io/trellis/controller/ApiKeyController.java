package io.trellis.controller;

import io.trellis.dto.ApiKeyCreateRequest;
import io.trellis.dto.ApiKeyResponse;
import io.trellis.entity.ApiKeyEntity;
import io.trellis.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    public List<ApiKeyResponse> list() {
        return apiKeyService.listApiKeys().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse create(@RequestBody ApiKeyCreateRequest request) {
        ApiKeyService.ApiKeyCreationResult result = apiKeyService.createApiKey(
                request.getLabel(), null);
        return ApiKeyResponse.builder()
                .id(result.entity().getId())
                .label(result.entity().getLabel())
                .keyPrefix(result.entity().getKeyPrefix())
                .userId(result.entity().getUserId())
                .createdAt(result.entity().getCreatedAt())
                .expiresAt(result.entity().getExpiresAt())
                .apiKey(result.rawKey())
                .build();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        apiKeyService.deleteApiKey(id);
    }

    private ApiKeyResponse toResponse(ApiKeyEntity entity) {
        return ApiKeyResponse.builder()
                .id(entity.getId())
                .label(entity.getLabel())
                .keyPrefix(entity.getKeyPrefix())
                .userId(entity.getUserId())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .build();
    }
}
