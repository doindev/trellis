package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "ldap",
        displayName = "Ldap",
        description = "Ldap authentication",
        category = "Infrastructure",
        icon = "ldap"
)
public class LdapCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("hostname").displayName("LDAP Server Address")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("port").displayName("Port")
                        .type(ParameterType.NUMBER)
                        .defaultValue(389).build(),
                NodeParameter.builder()
                        .name("bindDN").displayName("Bind DN")
                        .type(ParameterType.STRING).build(),
                NodeParameter.builder()
                        .name("bindPassword").displayName("Bind Password")
                        .type(ParameterType.STRING)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
