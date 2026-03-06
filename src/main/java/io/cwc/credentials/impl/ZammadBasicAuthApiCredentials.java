package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "zammadBasicAuthApi",
        displayName = "Zammad Basic Auth API",
        description = "Zammad Basic Auth API authentication",
        category = "Other",
        icon = "zammadbasicauth"
)
public class ZammadBasicAuthApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("baseUrl").displayName("Base URL")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
