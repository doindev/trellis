package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
