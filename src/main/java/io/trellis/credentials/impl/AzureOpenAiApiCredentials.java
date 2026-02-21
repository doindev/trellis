package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "azureOpenAiApi",
        displayName = "Azure OpenAI API",
        description = "Azure OpenAI API authentication",
        category = "AI / LLM",
        icon = "azure"
)
public class AzureOpenAiApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKey").displayName("API Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("endpoint").displayName("Endpoint")
                        .type(ParameterType.STRING).required(true)
                        .placeHolder("https://your-resource.openai.azure.com")
                        .description("Azure OpenAI resource endpoint URL")
                        .build(),
                NodeParameter.builder()
                        .name("apiVersion").displayName("API Version")
                        .type(ParameterType.STRING)
                        .defaultValue("2024-02-15-preview")
                        .description("Azure OpenAI API version")
                        .build()
        );
    }
}
