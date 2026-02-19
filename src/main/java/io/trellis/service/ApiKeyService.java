package io.trellis.service;

import io.trellis.entity.ApiKeyEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    public List<ApiKeyEntity> listApiKeys() {
        return apiKeyRepository.findAll();
    }

    /**
     * Creates a new API key. Returns the full key string only once; it is not stored.
     */
    @Transactional
    public ApiKeyCreationResult createApiKey(String label, String userId) {
        String rawKey = generateKey();
        String prefix = rawKey.substring(0, 8);
        String hash = hashKey(rawKey);

        ApiKeyEntity entity = ApiKeyEntity.builder()
                .label(label)
                .keyHash(hash)
                .keyPrefix(prefix)
                .userId(userId)
                .build();
        entity = apiKeyRepository.save(entity);

        return new ApiKeyCreationResult(entity, rawKey);
    }

    @Transactional
    public void deleteApiKey(String id) {
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("API key not found: " + id));
        apiKeyRepository.delete(entity);
    }

    public boolean validateKey(String rawKey) {
        String hash = hashKey(rawKey);
        return apiKeyRepository.findByKeyHash(hash).isPresent();
    }

    private String generateKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "tsk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }

    public record ApiKeyCreationResult(ApiKeyEntity entity, String rawKey) {}
}
