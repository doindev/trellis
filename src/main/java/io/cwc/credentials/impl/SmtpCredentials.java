package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "smtp",
        displayName = "Smtp",
        description = "Smtp authentication",
        category = "Email",
        icon = "smtp"
)
public class SmtpCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("user").displayName("User")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER)
                        .defaultValue(465).build(),
                NodeParameter.builder()
                        .name("secure").displayName("SSL/TLS")
                        .type(ParameterType.BOOLEAN)
                        .defaultValue(true).build()
        );
    }
}
