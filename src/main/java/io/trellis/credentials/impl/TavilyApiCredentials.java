package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "tavilyApi",
        displayName = "Tavily API",
        description = "Tavily web search API key authentication",
        category = "AI / LLM",
        icon = "tavily"
)
public class TavilyApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKey").displayName("API Key")
                        .type(ParameterType.STRING).required(true)
                        .description("Get your API key from tavily.com")
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
