package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "oAuth2Api",
        displayName = "OAuth2 API",
        description = "OAuth2 authentication",
        category = "Generic"
)
public class OAuth2Credentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("grantType").displayName("Grant Type")
                        .type(ParameterType.OPTIONS)
                        .options(List.of(
                                NodeParameter.ParameterOption.builder()
                                        .name("Authorization Code").value("authorizationCode").build(),
                                NodeParameter.ParameterOption.builder()
                                        .name("Client Credentials").value("clientCredentials").build()
                        ))
                        .defaultValue("authorizationCode").build(),
                NodeParameter.builder()
                        .name("authorizationUrl").displayName("Authorization URL")
                        .type(ParameterType.STRING)
                        .displayOptions(Map.of("show", Map.of("grantType", List.of("authorizationCode"))))
                        .build(),
                NodeParameter.builder()
                        .name("accessTokenUrl").displayName("Access Token URL")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("clientId").displayName("Client ID")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("clientSecret").displayName("Client Secret")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("scope").displayName("Scope")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("accessToken").displayName("Access Token")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
