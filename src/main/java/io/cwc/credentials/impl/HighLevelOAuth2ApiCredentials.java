package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "highLevelOAuth2Api",
        displayName = "HighLevel OAuth2 API",
        description = "HighLevel OAuth2 API authentication",
        category = "CRM / Sales",
        icon = "highlevel",
        extendsType = "oAuth2Api"
)
public class HighLevelOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://marketplace.gohighlevel.com/oauth/chooselocation")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://services.leadconnectorhq.com/oauth/token")
                        .build()
        );
    }
}
