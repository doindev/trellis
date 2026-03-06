package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "salesforceJwtApi",
        displayName = "Salesforce Jwt API",
        description = "Salesforce Jwt API authentication",
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
