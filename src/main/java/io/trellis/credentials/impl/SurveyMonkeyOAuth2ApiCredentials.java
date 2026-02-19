package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "surveyMonkeyOAuth2Api",
        displayName = "SurveyMonkey O Auth2 A P I",
        description = "SurveyMonkey O Auth2 A P I authentication",
        category = "Marketing",
        icon = "surveymonkey",
        extendsType = "oAuth2Api"
)
public class SurveyMonkeyOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://api.surveymonkey.com/oauth/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://api.surveymonkey.com/oauth/token")
                        .build()
        );
    }
}
