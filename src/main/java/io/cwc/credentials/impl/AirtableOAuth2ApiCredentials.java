package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "airtableOAuth2Api",
        displayName = "Airtable OAuth2 API",
        description = "Airtable OAuth2 API authentication",
        category = "Productivity",
        icon = "airtable",
        extendsType = "oAuth2Api"
)
public class AirtableOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://airtable.com/oauth2/v1/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://airtable.com/oauth2/v1/token")
                        .build()
        );
    }
}
