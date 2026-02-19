package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "notionApi",
        displayName = "Notion API",
        description = "Notion API integration token authentication",
        category = "Developer Tools",
        icon = "notion"
)
public class NotionApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKeyNotice").displayName("API Key Notice")
                        .type(ParameterType.NOTICE)
                        .description("Create an integration at notion.so/my-integrations to get your API key.")
                        .build(),
                NodeParameter.builder()
                        .name("apiKey").displayName("Internal Integration Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
