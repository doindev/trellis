package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
