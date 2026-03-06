package io.cwc.credentials.impl;

import java.util.List;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;

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
