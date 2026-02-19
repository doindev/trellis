package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "acuitySchedulingOAuth2Api",
        displayName = "Acuity Scheduling OAuth2 API",
        description = "Acuity Scheduling OAuth2 API authentication",
        category = "Marketing",
        icon = "acuityscheduling",
        extendsType = "oAuth2Api"
)
public class AcuitySchedulingOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://acuityscheduling.com/oauth2/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://acuityscheduling.com/oauth2/token")
                        .build()
        );
    }
}
