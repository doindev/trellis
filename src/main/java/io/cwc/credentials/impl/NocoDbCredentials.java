package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "nocoDb",
        displayName = "Noco DB",
        description = "Noco DB authentication",
        category = "Productivity",
        icon = "noco"
)
public class NocoDbCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiToken").displayName("API Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING)
                        .defaultValue("https://app.nocodb.com").build()
        );
    }
}
