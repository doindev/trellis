package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
		type = "motorheadApi",
		displayName = "Motorhead API",
		description = "Motorhead memory server credentials",
		category = "AI / Memory",
		icon = "database"
)
public class MotorheadApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("host").displayName("Host")
						.type(ParameterType.STRING).required(true)
						.defaultValue("http://localhost:8080")
						.description("Motorhead server URL")
						.build(),
				NodeParameter.builder()
						.name("clientId").displayName("Client ID")
						.type(ParameterType.STRING)
						.description("Optional client ID for authentication")
						.build(),
				NodeParameter.builder()
						.name("apiKey").displayName("API Key")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("password", true))
						.description("Optional API key for authentication")
						.build()
		);
	}
}
