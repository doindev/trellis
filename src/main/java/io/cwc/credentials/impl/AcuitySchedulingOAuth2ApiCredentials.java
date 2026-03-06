package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
