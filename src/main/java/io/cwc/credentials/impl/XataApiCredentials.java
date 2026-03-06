package io.cwc.credentials.impl;

import java.util.List;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
		type = "xataApi",
		displayName = "Xata API",
		description = "Xata database API credentials",
		category = "Databases",
		icon = "database"
)
public class XataApiCredentials implements CredentialProviderInterface {

	@Override
	public List<NodeParameter> getProperties() {
		return List.of(
				NodeParameter.builder()
						.name("apiKey").displayName("API Key")
						.type(ParameterType.STRING).required(true)
						.typeOptions(java.util.Map.of("password", true))
						.description("Your Xata API key")
						.build(),
				NodeParameter.builder()
						.name("databaseEndpoint").displayName("Database Endpoint")
						.type(ParameterType.STRING).required(true)
						.description("The Xata database endpoint URL (e.g. https://workspace.region.xata.sh/db/dbname)")
						.build(),
				NodeParameter.builder()
						.name("branch").displayName("Branch")
						.type(ParameterType.STRING)
						.defaultValue("main")
						.description("The Xata database branch")
						.build()
		);
	}
}
