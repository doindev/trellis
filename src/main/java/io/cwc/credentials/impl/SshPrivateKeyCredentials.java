package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "sshPrivateKey",
        displayName = "Ssh Private Key",
        description = "Ssh Private Key authentication",
        category = "Infrastructure",
        icon = "sshprivatekey"
)
public class SshPrivateKeyCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("host").displayName("Host")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER)
                        .defaultValue(22).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("privateKey").displayName("Private Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("passphrase").displayName("Passphrase")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
