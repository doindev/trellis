package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
