package io.cwc.controller;

import io.cwc.entity.McpApiTokenEntity;
import io.cwc.service.McpApiTokenService;
import io.cwc.util.SecurityContextHelper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp-tokens")
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration")
@ConditionalOnProperty(name = "cwc.features.mcp-server.enabled", havingValue = "true", matchIfMissing = true)
public class McpTokenController {

    private final McpApiTokenService tokenService;
    private final SecurityContextHelper securityContextHelper;

    @GetMapping
    public List<TokenResponse> listTokens(
            @RequestParam(required = false) String endpointId) {
        String userId = securityContextHelper.getCurrentUserId();
        return tokenService.listTokens(userId, endpointId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createToken(@RequestBody CreateTokenRequest request) {
        String userId = securityContextHelper.getCurrentUserId();
        Instant expiresAt = resolveExpiration(request.getExpirationDays());

        String rawToken = tokenService.createToken(
                request.getName(), userId, request.getEndpointId(), expiresAt);

        return Map.of(
            "token", rawToken,
            "message", "This is the only time this token value will be shown. Copy it now."
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteToken(@PathVariable String id) {
        String userId = securityContextHelper.getCurrentUserId();
        tokenService.deleteToken(id, userId);
    }

    private Instant resolveExpiration(Integer days) {
        if (days == null || days <= 0) return null; // no expiration
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private TokenResponse toResponse(McpApiTokenEntity entity) {
        TokenResponse r = new TokenResponse();
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setTokenPrefix(entity.getTokenPrefix());
        r.setCreatedAt(entity.getCreatedAt().toString());
        r.setExpiresAt(entity.getExpiresAt() != null ? entity.getExpiresAt().toString() : null);
        r.setExpired(entity.isExpired());
        return r;
    }

    @Data
    public static class CreateTokenRequest {
        private String name;
        private String endpointId;
        private Integer expirationDays; // null or 0 = never, 7, 30, 60, 90, 180, 365
    }

    @Data
    public static class TokenResponse {
        private String id;
        private String name;
        private String tokenPrefix;
        private String createdAt;
        private String expiresAt;
        private boolean expired;
    }
}
