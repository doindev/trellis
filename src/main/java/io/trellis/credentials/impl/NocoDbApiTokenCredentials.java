package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "nocoDbApiToken",
        displayName = "NocoDB Api Token",
        description = "NocoDB Api Token authentication",
        category = "Productivity",
        icon = "nocodbapitoken"
)
public class NocoDbApiTokenCredentials implements CredentialProviderInterface {

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
