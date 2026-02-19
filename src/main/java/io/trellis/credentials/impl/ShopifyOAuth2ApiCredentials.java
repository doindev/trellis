package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "shopifyOAuth2Api",
        displayName = "Shopify O Auth2 A P I",
        description = "Shopify O Auth2 A P I authentication",
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
