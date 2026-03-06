package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "anthropicApi",
        displayName = "Anthropic API",
        description = "Anthropic API key authentication",
        category = "AI / LLM",
        icon = "anthropic"
)
public class AnthropicApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKeyNotice").displayName("API Key Notice")
                        .type(ParameterType.NOTICE)
                        .description("Get your API key from console.anthropic.com")
                        .build(),
                NodeParameter.builder()
                        .name("apiKey").displayName("API Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("baseUrl").displayName("Base URL")
                        .type(ParameterType.STRING)
                        .defaultValue("https://api.anthropic.com/v1")
                        .description("Override for proxies or compatible APIs")
                        .build()
        );
    }
}
