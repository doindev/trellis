package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "shopifyAccessTokenApi",
        displayName = "Shopify Access Token API",
        description = "Shopify Access Token API authentication",
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
