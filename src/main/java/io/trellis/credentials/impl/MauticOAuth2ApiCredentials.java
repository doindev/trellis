package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;


import java.util.List;


@CredentialProvider(
        type = "mauticOAuth2Api",
        displayName = "Mautic OAuth2 API",
        description = "Mautic OAuth2 API authentication",
        category = "CRM / Sales",
        icon = "mautic",
        extendsType = "oAuth2Api"
)
public class MauticOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }
}
