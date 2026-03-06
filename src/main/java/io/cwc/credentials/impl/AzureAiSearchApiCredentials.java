package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
		type = "azureAiSearchApi",
		displayName = "Azure AI Search API",
		description = "Azure AI Search (formerly Azure Cognitive Search) connection",
		category = "AI / Vector Stores",
		icon = "azure"
)
public class AzureAiSearchApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("endpoint").displayName("Endpoint")
						.type(ParameterType.STRING).required(true)
						.description("Azure AI Search endpoint URL (e.g. https://myservice.search.windows.net)").build(),
				NodeParameter.builder()
						.name("apiKey").displayName("API Key")
						.type(ParameterType.STRING).required(true)
						.typeOptions(Map.of("password", true)).build()
		);
	}
}
