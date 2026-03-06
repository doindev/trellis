package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "oAuth1Api",
        displayName = "OAuth1 API",
        description = "OAuth1 API authentication",
        category = "Generic"
)
public class OAuth1ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("consumerKey").displayName("Consumer Key")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("consumerSecret").displayName("Consumer Secret")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("requestTokenUrl").displayName("Request Token URL")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("authorizationUrl").displayName("Authorization URL")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("accessTokenUrl").displayName("Access Token URL")
                        .type(ParameterType.STRING).required(true).build()
        );
    }
}
