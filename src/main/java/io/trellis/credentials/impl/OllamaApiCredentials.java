package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
	                .name("apiKey").displayName("API Key")
	                .type(ParameterType.STRING).required(true)
	                .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                    .name("baseUrl").displayName("Base URL")
                    .type(ParameterType.STRING).required(true)
                    .defaultValue("http://localhost:11434")
                    .description("URL of the Ollama server")
                    .build()
        );
    }
}
