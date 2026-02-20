package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;

import java.util.List;

@CredentialProvider(
        type = "serviceNowOAuth2Api",
        displayName = "Service Now OAuth2 API",
        description = "Service Now OAuth2 API authentication",
        category = "Support",
        icon = "servicenow",
        extendsType = "oAuth2Api"
)
public class ServiceNowOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }
}
