package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "microsoftAzureCosmosDbSharedKeyApi",
        displayName = "Azure Cosmos DB Shared Key A P I",
        description = "Azure Cosmos DB Shared Key A P I authentication",
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
