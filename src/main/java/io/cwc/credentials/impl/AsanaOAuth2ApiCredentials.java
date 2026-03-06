package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "asanaOAuth2Api",
        displayName = "Asana OAuth2 API",
        description = "Asana OAuth2 API authentication",
        category = "Project Management",
        icon = "asana",
        extendsType = "oAuth2Api"
)
public class AsanaOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://app.asana.com/-/oauth_authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://app.asana.com/-/oauth_token")
                        .build()
        );
    }
}
