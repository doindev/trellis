package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "microsoftSql",
        displayName = "Microsoft SQL Server",
        description = "Microsoft SQL Server authentication",
        category = "Microsoft Services",
        icon = "microsoftsql"
)
public class MicrosoftSqlCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("server").displayName("Server")
                        .type(ParameterType.STRING)
                        .defaultValue("localhost").build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER)
                        .defaultValue(1433).build(),
                NodeParameter.builder()
                        .name("database").displayName("Database")
                        .type(ParameterType.STRING)
                        .defaultValue("master").build(),
                NodeParameter.builder()
                        .name("user").displayName("User")
                        .type(ParameterType.STRING)
                        .defaultValue("sa").build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("tls").displayName("Encrypt")
                        .type(ParameterType.BOOLEAN)
                        .defaultValue(true).build()
        );
    }
}
