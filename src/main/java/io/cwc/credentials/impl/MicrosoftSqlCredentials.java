package io.cwc.credentials.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.cwc.credentials.ConnectionPoolParameters;
import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
        var params = new ArrayList<>(List.of(
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
        ));
        params.add(ConnectionPoolParameters.connectionPoolParameter());
        return params;
    }
}
