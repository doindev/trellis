package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "bitlyOAuth2Api",
        displayName = "Bitly O Auth2 A P I",
        description = "Bitly O Auth2 A P I authentication",
        category = "Other",
        icon = "bitly",
        extendsType = "oAuth2Api"
)
public class BitlyOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://bitly.com/oauth/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://api-ssl.bitly.com/oauth/access_token")
                        .build()
        );
    }
}
