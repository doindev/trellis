package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
