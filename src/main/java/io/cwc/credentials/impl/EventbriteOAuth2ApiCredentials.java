package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "eventbriteOAuth2Api",
        displayName = "Eventbrite OAuth2 API",
        description = "Eventbrite OAuth2 API authentication",
        category = "Marketing",
        icon = "eventbrite",
        extendsType = "oAuth2Api"
)
public class EventbriteOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://www.eventbrite.com/oauth/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://www.eventbrite.com/oauth/token")
                        .build()
        );
    }
}
