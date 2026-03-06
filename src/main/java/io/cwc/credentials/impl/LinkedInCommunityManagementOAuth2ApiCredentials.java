package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "linkedInCommunityManagementOAuth2Api",
        displayName = "LinkedIn Community Management OAuth2 API",
        description = "LinkedIn Community Management OAuth2 API authentication",
        category = "Social Media",
        icon = "linkedincommunitymanagement",
        extendsType = "oAuth2Api"
)
public class LinkedInCommunityManagementOAuth2ApiCredentials implements CredentialProviderInterface {

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
