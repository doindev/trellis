package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "quickBooksOAuth2Api",
        displayName = "QuickBooks O Auth2 A P I",
        description = "QuickBooks O Auth2 A P I authentication",
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
