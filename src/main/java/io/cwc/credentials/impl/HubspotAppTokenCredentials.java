package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "hubspotAppToken",
        displayName = "Hubspot App Token",
        description = "Hubspot App Token authentication",
        category = "CRM / Sales",
        icon = "hubspotapptoken"
)
public class HubspotAppTokenCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("appToken").displayName("App Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
