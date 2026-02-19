package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "jwtAuth",
        displayName = "Jwt Auth",
        description = "Jwt Auth authentication",
        category = "Generic",
        icon = "jwtauth"
)
public class JwtAuthCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("keyType").displayName("Key Type")
                        .type(ParameterType.STRING)
                        .defaultValue("passphrase").build(),
                NodeParameter.builder()
                        .name("secret").displayName("Secret / Private Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("algorithm").displayName("Algorithm")
                        .type(ParameterType.STRING)
                        .defaultValue("HS256").build()
        );
    }
}
