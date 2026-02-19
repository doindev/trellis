package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "microsoftOutlookOAuth2Api",
        displayName = "Microsoft Outlook OAuth2 API",
        description = "Microsoft Outlook OAuth2 API authentication",
        category = "Microsoft Services",
        icon = "microsoftoutlook",
        extendsType = "oAuth2Api"
)
public class MicrosoftOutlookOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                        .build()
        );
    }
}
