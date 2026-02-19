package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "helpScoutOAuth2Api",
        displayName = "HelpScout O Auth2 A P I",
        description = "HelpScout O Auth2 A P I authentication",
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
