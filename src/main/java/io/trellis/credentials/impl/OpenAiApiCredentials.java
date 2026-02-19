package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "openAiApi",
        displayName = "OpenAI API",
        description = "OpenAI API key authentication",
        category = "AI / LLM",
        icon = "openai"
)
public class OpenAiApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKeyNotice").displayName("API Key Notice")
                        .type(ParameterType.NOTICE)
                        .description("Get your API key from platform.openai.com")
                        .build(),
                NodeParameter.builder()
                        .name("apiKey").displayName("API Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
