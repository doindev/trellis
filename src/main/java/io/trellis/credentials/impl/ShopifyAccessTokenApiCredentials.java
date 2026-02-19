package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "shopifyAccessTokenApi",
        displayName = "Shopify Access Token A P I",
        description = "Shopify Access Token A P I authentication",
        category = "Finance",
        icon = "shopifyaccesstoken"
)
public class ShopifyAccessTokenApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("accessToken").displayName("Access Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("shopSubdomain").displayName("Shop Subdomain")
                        .type(ParameterType.STRING).required(true).build()
        );
    }
}
