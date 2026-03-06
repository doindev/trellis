package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "mqtt",
        displayName = "Mqtt",
        description = "Mqtt authentication",
        category = "Infrastructure",
        icon = "mqtt"
)
public class MqttCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER)
                        .defaultValue(1883).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("ssl").displayName("SSL")
                        .type(ParameterType.BOOLEAN)
                        .defaultValue(false).build()
        );
    }
}
