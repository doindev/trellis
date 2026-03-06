package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "questDb",
        displayName = "Quest DB",
        description = "Quest DB authentication",
        category = "Databases",
        icon = "quest"
)
public class QuestDbCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING)
                        .defaultValue("localhost").build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER)
                        .defaultValue(8812).build(),
                NodeParameter.builder()
                        .name("database").displayName("Database")
                        .type(ParameterType.STRING).build(),
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
