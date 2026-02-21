package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
		type = "chromaApi",
		displayName = "ChromaDB API",
		description = "ChromaDB vector database connection",
		category = "AI / Vector Stores",
		icon = "database"
)
public class ChromaApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).required(true)
						.defaultValue("http://localhost:8000")
						.description("ChromaDB server URL").build(),
				NodeParameter.builder()
						.name("tenant").displayName("Tenant")
						.type(ParameterType.STRING)
						.defaultValue("default_tenant")
						.description("ChromaDB tenant name").build(),
				NodeParameter.builder()
						.name("database").displayName("Database")
						.type(ParameterType.STRING)
						.defaultValue("default_database")
						.description("ChromaDB database name").build(),
				NodeParameter.builder()
						.name("apiKey").displayName("API Key")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("password", true))
						.description("Optional API key for authentication").build()
		);
	}
}
