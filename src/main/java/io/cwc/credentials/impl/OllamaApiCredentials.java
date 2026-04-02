package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "ollamaApi",
        displayName = "Ollama",
        description = "Ollama local LLM server connection",
        category = "AI / LLM",
        icon = "ollama"
)
public class OllamaApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                    .name("baseUrl").displayName("Base URL")
                    .type(ParameterType.STRING).required(true)
                    .defaultValue("http://localhost:11434")
                    .description("URL of the Ollama server")
                    .build(),
                NodeParameter.builder()
                    .name("apiKey").displayName("API Key")
                    .type(ParameterType.STRING).required(false)
                    .typeOptions(Map.of("password", true))
                    .description("Optional — only needed if Ollama is configured with authentication")
                    .build()
        );
    }
}
