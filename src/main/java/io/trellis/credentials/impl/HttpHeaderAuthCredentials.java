package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "httpHeaderAuth",
        displayName = "HTTP Header Auth",
        description = "Authentication via custom HTTP header",
        category = "Generic"
)
public class HttpHeaderAuthCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("name").displayName("Header Name")
                        .type(ParameterType.STRING).required(true)
                        .defaultValue("Authorization").build(),
                NodeParameter.builder()
                        .name("value").displayName("Header Value")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
