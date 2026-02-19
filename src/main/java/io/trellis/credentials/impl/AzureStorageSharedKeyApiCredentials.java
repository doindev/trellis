package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "azureStorageSharedKeyApi",
        displayName = "Azure Storage Shared Key API",
        description = "Azure Storage Shared Key API authentication",
        category = "Cloud Services",
        icon = "azurestoragesharedkey"
)
public class AzureStorageSharedKeyApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("accountName").displayName("Account Name")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("accountKey").displayName("Account Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
