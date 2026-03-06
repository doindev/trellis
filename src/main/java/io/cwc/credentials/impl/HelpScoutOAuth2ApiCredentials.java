package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "helpScoutOAuth2Api",
        displayName = "HelpScout OAuth2 API",
        description = "HelpScout OAuth2 API authentication",
        category = "Support",
        icon = "helpscout",
        extendsType = "oAuth2Api"
)
public class HelpScoutOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://secure.helpscout.net/authentication/authorizeClientApplication")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://api.helpscout.net/v2/oauth2/token")
                        .build()
        );
    }
}
