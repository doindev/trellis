package io.cwc.credentials.impl;

import java.util.List;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;

@CredentialProvider(
        type = "shopifyOAuth2Api",
        displayName = "Shopify OAuth2 API",
        description = "Shopify OAuth2 API authentication",
        category = "Finance",
        icon = "shopify",
        extendsType = "oAuth2Api"
)
public class ShopifyOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }
}
