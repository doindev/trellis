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
        type = "mysqlApi",
        displayName = "MySQL",
        description = "MySQL database connection credentials",
        category = "Databases",
        icon = "mysql"
)
public class MySqlCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        var params = new ArrayList<>(List.of(
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING).required(true)
                        .defaultValue("localhost").build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER).required(true)
                        .defaultValue(3306).build(),
                NodeParameter.builder()
                        .name("database").displayName("Database")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        ));
        params.add(ConnectionPoolParameters.connectionPoolParameter());
        return params;
    }
}
