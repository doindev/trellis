package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
		type = "weaviateApi",
		displayName = "Weaviate API",
		description = "Weaviate vector database connection",
		category = "AI / Vector Stores",
		icon = "database"
)
public class WeaviateApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("host").displayName("Host")
						.type(ParameterType.STRING).required(true)
						.defaultValue("localhost:8080")
						.description("Weaviate host (e.g. localhost:8080 or my-cluster.weaviate.network)").build(),
				NodeParameter.builder()
						.name("scheme").displayName("Scheme")
						.type(ParameterType.OPTIONS)
						.defaultValue("http")
						.options(List.of(
								ParameterOption.builder().name("HTTP").value("http").build(),
								ParameterOption.builder().name("HTTPS").value("https").build()
						)).build(),
				NodeParameter.builder()
						.name("apiKey").displayName("API Key")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("password", true))
						.description("Optional API key for authentication").build()
		);
	}
}
