package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "httpQueryAuth",
        displayName = "HTTP Query Auth",
        description = "Authentication via query parameter",
        category = "Generic"
)
public class HttpQueryAuthCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("name").displayName("Parameter Name")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("value").displayName("Parameter Value")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
