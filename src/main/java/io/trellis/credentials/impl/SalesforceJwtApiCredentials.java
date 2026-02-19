package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "salesforceJwtApi",
        displayName = "Salesforce Jwt A P I",
        description = "Salesforce Jwt A P I authentication",
        category = "CRM / Sales",
        icon = "salesforcejwt"
)
public class SalesforceJwtApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("clientId").displayName("Client ID")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("privateKey").displayName("Private Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("username").displayName("Username")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("environment").displayName("Environment")
                        .type(ParameterType.STRING)
                        .defaultValue("production").build()
        );
    }
}
