package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "salesforceOAuth2Api",
        displayName = "Salesforce O Auth2 A P I",
        description = "Salesforce O Auth2 A P I authentication",
        category = "CRM / Sales",
        icon = "salesforce",
        extendsType = "oAuth2Api"
)
public class SalesforceOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://login.salesforce.com/services/oauth2/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://login.salesforce.com/services/oauth2/token")
                        .build()
        );
    }
}
