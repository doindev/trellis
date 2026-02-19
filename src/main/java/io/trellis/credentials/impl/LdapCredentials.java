package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
