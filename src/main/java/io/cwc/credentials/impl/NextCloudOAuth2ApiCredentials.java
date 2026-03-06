package io.cwc.credentials.impl;

import java.util.List;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;

@CredentialProvider(
        type = "nextCloudOAuth2Api",
        displayName = "Next Cloud OAuth2 API",
        description = "Next Cloud OAuth2 API authentication",
        category = "Storage",
        icon = "nextcloud",
        extendsType = "oAuth2Api"
)
public class NextCloudOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }
}
