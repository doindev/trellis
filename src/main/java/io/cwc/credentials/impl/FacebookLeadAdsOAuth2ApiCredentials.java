package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "facebookLeadAdsOAuth2Api",
        displayName = "Facebook Lead Ads OAuth2 API",
        description = "Facebook Lead Ads OAuth2 API authentication",
        category = "Social Media",
        icon = "facebookleadads",
        extendsType = "oAuth2Api"
)
public class FacebookLeadAdsOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://www.facebook.com/v17.0/dialog/oauth")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://graph.facebook.com/v17.0/oauth/access_token")
                        .build()
        );
    }
}
