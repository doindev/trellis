package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "googleSheetsOAuth2Api",
        displayName = "Google Sheets O Auth2 A P I",
        description = "Google Sheets O Auth2 A P I authentication",
        category = "Google Services",
        icon = "googlesheets",
        extendsType = "oAuth2Api"
)
public class GoogleSheetsOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://accounts.google.com/o/oauth2/v2/auth")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://oauth2.googleapis.com/token")
                        .build()
        );
    }
}
