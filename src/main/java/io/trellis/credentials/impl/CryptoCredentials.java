package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "crypto",
        displayName = "Crypto",
        description = "Crypto authentication",
        category = "Generic",
        icon = "crypto"
)
public class CryptoCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("value").displayName("Value")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("algorithm").displayName("Algorithm")
                        .type(ParameterType.STRING)
                        .defaultValue("sha256").build()
        );
    }
}
