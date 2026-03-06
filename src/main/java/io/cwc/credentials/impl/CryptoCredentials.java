package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
