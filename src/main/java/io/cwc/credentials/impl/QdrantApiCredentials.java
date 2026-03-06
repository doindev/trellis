package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
		type = "qdrantApi",
		displayName = "Qdrant API",
		description = "Qdrant vector database connection",
		category = "AI / Vector Stores",
		icon = "database"
)
public class QdrantApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).required(true)
						.defaultValue("http://localhost:6333")
						.description("Qdrant server URL").build(),
				NodeParameter.builder()
						.name("apiKey").displayName("API Key")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("password", true))
						.description("Optional API key for authentication").build()
		);
	}
}
