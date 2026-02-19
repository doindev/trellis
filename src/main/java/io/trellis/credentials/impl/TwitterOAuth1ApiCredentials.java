package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "twitterOAuth1Api",
        displayName = "Twitter OAuth1 API",
        description = "Twitter OAuth1 API authentication",
        category = "Social Media",
        icon = "twitter"
)
public class TwitterOAuth1ApiCredentials implements CredentialProviderInterface {

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
                        .name("accessToken").displayName("Access Token")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("accessTokenSecret").displayName("Access Token Secret")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
