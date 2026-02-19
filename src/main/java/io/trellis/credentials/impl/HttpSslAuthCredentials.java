package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "httpSslAuth",
        displayName = "Http Ssl Auth",
        description = "Http Ssl Auth authentication",
        category = "Generic",
        icon = "httpsslauth"
)
public class HttpSslAuthCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("cert").displayName("Certificate (PEM)")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("key").displayName("Private Key (PEM)")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("passphrase").displayName("Passphrase")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
