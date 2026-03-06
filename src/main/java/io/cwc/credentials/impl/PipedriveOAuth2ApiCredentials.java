package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "pipedriveOAuth2Api",
        displayName = "Pipedrive OAuth2 API",
        description = "Pipedrive OAuth2 API authentication",
        category = "CRM / Sales",
        icon = "pipedrive",
        extendsType = "oAuth2Api"
)
public class PipedriveOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://oauth.pipedrive.com/oauth/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://oauth.pipedrive.com/oauth/token")
                        .build()
        );
    }
}
