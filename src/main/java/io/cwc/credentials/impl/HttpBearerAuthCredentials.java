package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "httpBearerAuth",
        displayName = "Http Bearer Auth",
        description = "Http Bearer Auth authentication",
        category = "Generic",
        icon = "httpbearerauth"
)
public class HttpBearerAuthCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("token").displayName("Bearer Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
