package io.cwc.credentials.impl;

import java.util.List;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;

@CredentialProvider(
        type = "zendeskOAuth2Api",
        displayName = "Zendesk OAuth2 API",
        description = "Zendesk OAuth2 API authentication",
        category = "Support",
        icon = "zendesk",
        extendsType = "oAuth2Api"
)
public class ZendeskOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }
}
