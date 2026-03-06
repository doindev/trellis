package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "imap",
        displayName = "Imap",
        description = "Imap authentication",
        category = "Email",
        icon = "imap"
)
public class ImapCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("user").displayName("User")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER)
                        .defaultValue(993).build(),
                NodeParameter.builder()
                        .name("secure").displayName("SSL/TLS")
                        .type(ParameterType.BOOLEAN)
                        .defaultValue(true).build()
        );
    }
}
