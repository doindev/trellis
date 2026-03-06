package io.cwc.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SecurityChainInfoService {

    @Data
    @Builder
    public static class SecurityChainInfo {
        private String name;
        private String displayName;
        private String description;
    }

    public List<SecurityChainInfo> getAvailableChains() {
        return List.of(
                SecurityChainInfo.builder()
                        .name("none")
                        .displayName("None")
                        .description("No authentication required")
                        .build(),
                SecurityChainInfo.builder()
                        .name("basicAuth")
                        .displayName("Basic Auth")
                        .description("HTTP Basic authentication")
                        .build(),
                SecurityChainInfo.builder()
                        .name("apiKey")
                        .displayName("API Key")
                        .description("API key via X-API-Key header")
                        .build(),
                SecurityChainInfo.builder()
                        .name("jwt")
                        .displayName("JWT")
                        .description("JWT bearer token validation")
                        .build(),
                SecurityChainInfo.builder()
                        .name("oauth2")
                        .displayName("OAuth2")
                        .description("OAuth2/OIDC resource server")
                        .build(),
                SecurityChainInfo.builder()
                        .name("session")
                        .displayName("Session")
                        .description("Form login with session cookies")
                        .build(),
                SecurityChainInfo.builder()
                        .name("entra")
                        .displayName("Microsoft Entra")
                        .description("Azure AD client credentials")
                        .build()
        );
    }

    public List<String> getChainNames() {
        return getAvailableChains().stream()
                .map(SecurityChainInfo::getName)
                .toList();
    }
}
