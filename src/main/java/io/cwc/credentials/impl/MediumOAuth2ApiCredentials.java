package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "mediumOAuth2Api",
        displayName = "Medium OAuth2 API",
        description = "Medium OAuth2 API authentication",
        category = "Social Media",
        icon = "medium",
        extendsType = "oAuth2Api"
)
public class MediumOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://medium.com/m/oauth/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://api.medium.com/v1/tokens")
                        .build()
        );
    }
}
