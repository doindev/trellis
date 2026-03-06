package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "googleAnalyticsOAuth2Api",
        displayName = "Google Analytics OAuth2 API",
        description = "Google Analytics OAuth2 API authentication",
        category = "Google",
        icon = "googleanalytics",
        extendsType = "oAuth2Api"
)
public class GoogleAnalyticsOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://accounts.google.com/o/oauth2/v2/auth")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://oauth2.googleapis.com/token")
                        .build()
        );
    }
}
