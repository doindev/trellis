package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.AiSettingsDto;
import io.cwc.entity.AiSettingsEntity;
import io.cwc.repository.AiSettingsRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSettingsService {

    private final AiSettingsRepository repository;
    private final CredentialEncryptionService encryptionService;

    @Setter(onMethod_ = {@Autowired, @Lazy})
    private SettingsWritebackService settingsWritebackService;

    public AiSettingsDto getSettings() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .map(this::toDto)
                .orElseGet(() -> AiSettingsDto.builder()
                        .provider("openai")
                        .model("gpt-4o-mini")
                        .enabled(false)
                        .maxToolIterations(10)
                        .build());
    }

    public AiSettingsEntity getEntity() {
        return repository.findFirstByOrderByCreatedAtAsc().orElse(null);
    }

    /** Returns the decrypted API key for the current AI settings, or null if not configured. */
    public String getDecryptedApiKey() {
        var entity = getEntity();
        if (entity == null || entity.getApiKey() == null || entity.getApiKey().isBlank()) return null;
        try {
            return encryptionService.decryptString(entity.getApiKey());
        } catch (Exception e) {
            // Key may be stored unencrypted (legacy) — return as-is
            log.debug("Could not decrypt AI settings API key, using raw value");
            return entity.getApiKey();
        }
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
        if (dto.getMaxToolIterations() > 0) {
            entity.setMaxToolIterations(dto.getMaxToolIterations());
        }
        entity.setDefaultAgentId(dto.getDefaultAgentId());

        // Only update API key if provided (non-null, non-blank); encrypt before storing
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) {
            entity.setApiKey(encryptionService.encryptString(dto.getApiKey()));
        }

        AiSettingsDto result = toDto(repository.save(entity));
        if (settingsWritebackService != null) settingsWritebackService.writeSettings();
        return result;
    }

    private AiSettingsDto toDto(AiSettingsEntity entity) {
        String displayKey = null;
        if (entity.getApiKey() != null && !entity.getApiKey().isBlank()) {
            try {
                String decrypted = encryptionService.decryptString(entity.getApiKey());
                displayKey = maskApiKey(decrypted);
            } catch (Exception e) {
                // Legacy unencrypted key — mask raw value
                displayKey = maskApiKey(entity.getApiKey());
            }
        }
        return AiSettingsDto.builder()
                .provider(entity.getProvider())
                .apiKey(displayKey)
                .model(entity.getModel())
                .baseUrl(entity.getBaseUrl())
                .enabled(entity.isEnabled())
                .maxToolIterations(entity.getMaxToolIterations())
                .defaultAgentId(entity.getDefaultAgentId())
                .build();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return apiKey == null ? null : "****";
        }
        return apiKey.substring(0, 3) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
