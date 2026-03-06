package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "oracleDBApi",
        displayName = "Oracle DB",
        description = "Oracle database connection credentials",
        category = "Databases",
        icon = "oracle"
)
public class OracleDBApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING).required(true)
                        .defaultValue("localhost").build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER).required(true)
                        .defaultValue(1521).build(),
                NodeParameter.builder()
                        .name("connectAs").displayName("Connect As")
                        .type(ParameterType.OPTIONS)
                        .defaultValue("serviceName")
                        .options(List.of(
                                ParameterOption.builder().name("Service Name").value("serviceName").build(),
                                ParameterOption.builder().name("SID").value("sid").build()
                        )).build(),
                NodeParameter.builder()
                        .name("database").displayName("Service Name / SID")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("password").displayName("Password")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
