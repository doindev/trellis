package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "kafka",
        displayName = "Kafka",
        description = "Kafka authentication",
        category = "Infrastructure",
        icon = "kafka"
)
public class KafkaCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("clientId").displayName("Client ID")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("brokers").displayName("Brokers")
                        .type(ParameterType.STRING).required(true)
                        .placeHolder("kafka1:9092,kafka2:9092").build(),
                NodeParameter.builder()
                        .name("ssl").displayName("SSL")
                        .type(ParameterType.BOOLEAN)
                        .defaultValue(true).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
