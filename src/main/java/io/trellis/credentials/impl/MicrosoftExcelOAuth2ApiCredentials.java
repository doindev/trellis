package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "microsoftExcelOAuth2Api",
        displayName = "Microsoft Excel O Auth2 A P I",
        description = "Microsoft Excel O Auth2 A P I authentication",
        category = "Microsoft Services",
        icon = "microsoftexcel",
        extendsType = "oAuth2Api"
)
public class MicrosoftExcelOAuth2ApiCredentials implements CredentialProviderInterface {

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
