package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "microsoftAzureCosmosDbSharedKeyApi",
        displayName = "Azure Cosmos DB Shared Key API",
        description = "Azure Cosmos DB Shared Key API authentication",
        category = "Microsoft Services",
        icon = "microsoftazurecosmosdbsharedkey"
)
public class MicrosoftAzureCosmosDbSharedKeyApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("endpoint").displayName("Endpoint")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("accountKey").displayName("Account Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
