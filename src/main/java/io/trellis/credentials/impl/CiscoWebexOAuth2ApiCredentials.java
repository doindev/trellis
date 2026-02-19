package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "ciscoWebexOAuth2Api",
        displayName = "Cisco Webex O Auth2 A P I",
        description = "Cisco Webex O Auth2 A P I authentication",
        category = "Communication",
        icon = "ciscowebex",
        extendsType = "oAuth2Api"
)
public class CiscoWebexOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://webexapis.com/v1/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://webexapis.com/v1/access_token")
                        .build()
        );
    }
}
