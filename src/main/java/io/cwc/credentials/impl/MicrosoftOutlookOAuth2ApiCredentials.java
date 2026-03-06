package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
