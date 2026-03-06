package io.cwc.credentials.impl;

import java.util.List;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;


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
