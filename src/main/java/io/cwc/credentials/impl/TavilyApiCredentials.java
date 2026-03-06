package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
