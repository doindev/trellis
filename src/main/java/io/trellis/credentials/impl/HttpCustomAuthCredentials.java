package io.trellis.credentials.impl;

import java.util.List;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "httpCustomAuth",
        displayName = "Http Custom Auth",
        description = "Http Custom Auth authentication",
        category = "Generic",
        icon = "httpcustomauth"
)
public class HttpCustomAuthCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("json").displayName("JSON Configuration")
                        .type(ParameterType.STRING).required(true).build()
        );
    }
}
