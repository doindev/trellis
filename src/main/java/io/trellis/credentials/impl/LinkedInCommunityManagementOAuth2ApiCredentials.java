package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "linkedInCommunityManagementOAuth2Api",
        displayName = "LinkedIn Community Management O Auth2 A P I",
        description = "LinkedIn Community Management O Auth2 A P I authentication",
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
