package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "neo4j",
        displayName = "Neo4j",
        description = "Neo4j graph database connection credentials",
        category = "Databases",
        icon = "neo4j"
)
public class Neo4jCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("uri").displayName("URI")
                        .type(ParameterType.STRING).required(true)
                        .defaultValue("bolt://localhost:7687").build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).required(true)
                        .defaultValue("neo4j").build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("database").displayName("Database")
                        .type(ParameterType.STRING)
                        .defaultValue("neo4j").build()
        );
    }
}
