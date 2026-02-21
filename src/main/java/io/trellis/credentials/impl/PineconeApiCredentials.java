package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
		type = "pineconeApi",
		displayName = "Pinecone API",
		description = "Pinecone vector database authentication",
		category = "AI / Vector Stores",
		icon = "database"
)
public class PineconeApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("apiKey").displayName("API Key")
						.type(ParameterType.STRING).required(true)
						.typeOptions(Map.of("password", true)).build(),
				NodeParameter.builder()
						.name("environment").displayName("Environment")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Pinecone environment (e.g. us-east-1-aws). Leave blank for serverless indexes.")
						.build()
		);
	}
}
