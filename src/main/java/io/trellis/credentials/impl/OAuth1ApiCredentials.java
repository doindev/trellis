package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "oAuth1Api",
        displayName = "O Auth1 A P I",
        description = "O Auth1 A P I authentication",
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
