package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
