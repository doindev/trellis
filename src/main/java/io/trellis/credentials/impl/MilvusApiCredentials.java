package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
		type = "milvusApi",
		displayName = "Milvus API",
		description = "Milvus vector database connection",
		category = "AI / Vector Stores",
		icon = "database"
)
public class MilvusApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).required(true)
						.defaultValue("http://localhost:19530")
						.description("Milvus server URL").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING)
						.description("Optional username").build(),
				NodeParameter.builder()
						.name("password").displayName("Password")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("password", true))
						.description("Optional password").build(),
				NodeParameter.builder()
						.name("token").displayName("Token")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("password", true))
						.description("Optional access token (for Zilliz Cloud)").build()
		);
	}
}
