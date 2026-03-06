package io.cwc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.AiSettingsDto;
import io.cwc.entity.AiSettingsEntity;
import io.cwc.repository.AiSettingsRepository;

@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiSettingsRepository repository;

    public AiSettingsDto getSettings() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .map(this::toDto)
                .orElseGet(() -> AiSettingsDto.builder()
                        .provider("openai")
                        .model("gpt-4o-mini")
                        .enabled(false)
                        .build());
    }

    public AiSettingsEntity getEntity() {
        return repository.findFirstByOrderByCreatedAtAsc().orElse(null);
    }

    public boolean isEnabled() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .map(AiSettingsEntity::isEnabled)
                .orElse(false);
    }

    @Transactional
    public AiSettingsDto saveSettings(AiSettingsDto dto) {
        AiSettingsEntity entity = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(AiSettingsEntity::new);

        if (dto.getProvider() != null) {
            entity.setProvider(dto.getProvider());
        }
        if (dto.getModel() != null) {
            entity.setModel(dto.getModel());
        }
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setEnabled(dto.isEnabled());

        // Only update API key if provided (non-null, non-blank)
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            entity.setApiKey(dto.getApiKey());
        }

        return toDto(repository.save(entity));
    }

    private AiSettingsDto toDto(AiSettingsEntity entity) {
        return AiSettingsDto.builder()
                .provider(entity.getProvider())
                .apiKey(maskApiKey(entity.getApiKey()))
                .model(entity.getModel())
                .baseUrl(entity.getBaseUrl())
                .enabled(entity.isEnabled())
                .build();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return apiKey == null ? null : "****";
        }
        return apiKey.substring(0, 3) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
