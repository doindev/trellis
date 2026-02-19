package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "highLevelOAuth2Api",
        displayName = "HighLevel O Auth2 A P I",
        description = "HighLevel O Auth2 A P I authentication",
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
