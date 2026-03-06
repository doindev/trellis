package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "quickBooksOAuth2Api",
        displayName = "QuickBooks OAuth2 API",
        description = "QuickBooks OAuth2 API authentication",
        category = "Finance",
        icon = "quickbooks",
        extendsType = "oAuth2Api"
)
public class QuickBooksOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://appcenter.intuit.com/connect/oauth2")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer")
                        .build()
        );
    }
}
