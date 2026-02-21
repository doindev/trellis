package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "googleAiApi",
        displayName = "Google AI (Gemini) API",
        description = "Google AI (Gemini) API key authentication",
        category = "AI / LLM",
        icon = "google"
)
public class GoogleAiApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKey").displayName("API Key")
                        .type(ParameterType.STRING).required(true)
                        .description("Get your API key from ai.google.dev")
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("baseUrl").displayName("Base URL")
                        .type(ParameterType.STRING)
                        .defaultValue("https://generativelanguage.googleapis.com")
                        .description("Override for proxies or compatible APIs")
                        .build()
        );
    }
}
