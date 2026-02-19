package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "zohoOAuth2Api",
        displayName = "Zoho O Auth2 A P I",
        description = "Zoho O Auth2 A P I authentication",
        category = "Other",
        icon = "zoho",
        extendsType = "oAuth2Api"
)
public class ZohoOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://accounts.zoho.com/oauth/v2/auth")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://accounts.zoho.com/oauth/v2/token")
                        .build()
        );
    }
}
