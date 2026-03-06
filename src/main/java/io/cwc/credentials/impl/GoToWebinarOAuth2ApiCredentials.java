package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "goToWebinarOAuth2Api",
        displayName = "GoToWebinar OAuth2 API",
        description = "GoToWebinar OAuth2 API authentication",
        category = "Marketing",
        icon = "gotowebinar",
        extendsType = "oAuth2Api"
)
public class GoToWebinarOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://api.getgo.com/oauth/v2/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://api.getgo.com/oauth/v2/token")
                        .build()
        );
    }
}
