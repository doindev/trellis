package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
