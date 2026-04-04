package io.cwc.service;

import io.cwc.entity.McpAccessLogEntity;
import io.cwc.entity.McpApiTokenEntity;
import io.cwc.entity.McpEndpointEntity;
import io.cwc.exception.BadRequestException;
import io.cwc.exception.ForbiddenException;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.McpAccessLogRepository;
import io.cwc.repository.McpApiTokenRepository;
import io.cwc.repository.McpEndpointRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration")
@ConditionalOnProperty(name = "cwc.features.mcp-server.enabled", havingValue = "true", matchIfMissing = true)
public class McpApiTokenService {

    private static final int MAX_TOKENS_PER_USER_PER_ENDPOINT = 5;
    private static final String TOKEN_PREFIX = "mcp_";
    private static final int TOKEN_BYTES = 32;

    /** Cache token hash → entity for 5 minutes to avoid DB hits on every MCP request */
    private final Cache<String, Optional<McpApiTokenEntity>> tokenCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    /** Cache endpoint path → apiKeyRequired for 5 minutes */
    private final Cache<String, Boolean> endpointSecurityCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final McpApiTokenRepository tokenRepository;
    private final McpAccessLogRepository accessLogRepository;
    private final McpEndpointRepository endpointRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    // ── Token Management ──

    /**
     * Creates a new API token. Returns the raw token value (shown only once).
     */
    @Transactional
    public String createToken(String name, String userId, String endpointId, Instant expiresAt) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Token name is required");
        }

        long count = tokenRepository.countByUserIdAndMcpEndpointId(userId, endpointId);
        if (count >= MAX_TOKENS_PER_USER_PER_ENDPOINT) {
            throw new BadRequestException("Maximum of " + MAX_TOKENS_PER_USER_PER_ENDPOINT
                    + " tokens per endpoint. Delete an existing token first.");
        }

        // Verify endpoint exists
        endpointRepository.findById(endpointId)
                .orElseThrow(() -> new NotFoundException("MCP endpoint not found: " + endpointId));

        String rawToken = generateToken();
        String hash = hashToken(rawToken);

        McpApiTokenEntity entity = McpApiTokenEntity.builder()
                .name(name.trim())
                .tokenHash(hash)
                .tokenPrefix(rawToken.substring(0, Math.min(12, rawToken.length())) + "...")
                .userId(userId)
                .mcpEndpointId(endpointId)
                .expiresAt(expiresAt)
                .build();

        tokenRepository.save(entity);
        tokenCache.invalidateAll();
        log.info("Created MCP API token '{}' for user {} on endpoint {}", name, userId, endpointId);

        return rawToken;
    }

    public List<McpApiTokenEntity> listTokens(String userId, String endpointId) {
        if (endpointId != null) {
            return tokenRepository.findByUserIdAndMcpEndpointId(userId, endpointId);
        }
        return tokenRepository.findByUserId(userId);
    }

    @Transactional
    public void deleteToken(String tokenId, String userId) {
        McpApiTokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Token not found"));
        if (!token.getUserId().equals(userId)) {
            throw new ForbiddenException("You can only delete your own tokens");
        }
        tokenRepository.delete(token);
        tokenCache.invalidateAll();
        log.info("Deleted MCP API token '{}' (id={}) for user {}", token.getName(), tokenId, userId);
    }

    // ── Token Validation ──

    /**
     * Validates the API token from the request (cached for 5 min).
     * Returns the token entity if valid, empty if invalid/expired.
     */
    public Optional<McpApiTokenEntity> validateRequest(HttpServletRequest request) {
        String bearerToken = extractBearerToken(request);
        if (bearerToken == null) return Optional.empty();

        String hash = hashToken(bearerToken);
        return tokenCache.get(hash, k -> tokenRepository.findByTokenHash(k))
                .filter(t -> !t.isExpired());
    }

    /**
     * Checks if the given MCP path requires API key authentication (cached for 5 min).
     */
    public boolean isApiKeyRequired(String path) {
        return endpointSecurityCache.get(path, k ->
                endpointRepository.findByPath(k)
                        .map(McpEndpointEntity::isApiKeyRequired)
                        .orElse(false));
    }

    /** Evict caches when tokens or endpoint security settings change */
    public void evictCaches() {
        tokenCache.invalidateAll();
        endpointSecurityCache.invalidateAll();
    }

    // ── Access Logging ──

    public void logAccess(HttpServletRequest request, String endpointPath,
                          String username, String tokenName, String status, int responseCode) {
        try {
            McpAccessLogEntity logEntry = McpAccessLogEntity.builder()
                    .logDate(LocalDate.now())
                    .ipAddress(resolveClientIp(request))
                    .username(username != null ? username : "anonymous")
                    .tokenName(tokenName)
                    .endpointPath(endpointPath)
                    .requestUrl(request.getRequestURL().toString())
                    .httpMethod(request.getMethod())
                    .status(status)
                    .responseCode(responseCode)
                    .build();
            accessLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to write MCP access log: {}", e.getMessage());
        }
    }

    // Retention/purge is handled by McpAccessLogPartitionService
    // (drops PostgreSQL partitions or falls back to DELETE on H2)

    // ── IP Resolution ──

    /**
     * Resolves the real client IP considering proxy headers.
     */
    static String resolveClientIp(HttpServletRequest request) {
        // Standard proxy headers in priority order
        for (String header : List.of(
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP",          // Cloudflare
                "True-Client-IP",            // Akamai
                "X-Client-IP",
                "Forwarded")) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // X-Forwarded-For can contain multiple IPs; take the first (original client)
                String ip = value.split(",")[0].trim();
                // Handle RFC 7239 Forwarded header: "for=1.2.3.4"
                if (header.equals("Forwarded") && ip.toLowerCase().contains("for=")) {
                    ip = ip.replaceAll("(?i).*for=\"?([^;,\"]+)\"?.*", "$1");
                }
                if (!ip.isBlank()) return ip;
            }
        }
        return request.getRemoteAddr();
    }

    // ── Helpers ──

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        // Also check query param for SSE connections (can't set headers)
        return request.getParameter("token");
    }
}
