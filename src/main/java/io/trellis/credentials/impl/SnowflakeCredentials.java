package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "snowflake",
        displayName = "Snowflake",
        description = "Snowflake authentication",
        category = "Databases",
        icon = "snowflake"
)
public class SnowflakeCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("account").displayName("Account")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("database").displayName("Database")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("warehouse").displayName("Warehouse")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("schema").displayName("Schema")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("role").displayName("Role")
                        .type(ParameterType.STRING).build()
        );
    }
}
