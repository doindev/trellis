package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "linkedInOAuth2Api",
        displayName = "LinkedIn OAuth2 API",
        description = "LinkedIn OAuth2 API authentication",
        category = "Social Media",
        icon = "linkedin",
        extendsType = "oAuth2Api"
)
public class LinkedInOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://www.linkedin.com/oauth/v2/authorization")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://www.linkedin.com/oauth/v2/accessToken")
                        .build()
        );
    }
}
