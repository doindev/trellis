package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "zammadTokenAuthApi",
        displayName = "Zammad Token Auth API",
        description = "Zammad Token Auth API authentication",
        category = "Other",
        icon = "zammadtokenauth"
)
public class ZammadTokenAuthApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("baseUrl").displayName("Base URL")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("accessToken").displayName("Access Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
