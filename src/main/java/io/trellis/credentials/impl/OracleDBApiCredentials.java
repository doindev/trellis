package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
