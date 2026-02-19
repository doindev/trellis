package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
