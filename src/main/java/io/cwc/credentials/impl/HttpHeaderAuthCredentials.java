package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
