package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "githubOAuth2Api",
        displayName = "GitHub OAuth2 API",
        description = "GitHub OAuth2 authentication",
        category = "Developer Tools",
        icon = "github",
        extendsType = "oAuth2Api"
)
public class GitHubOAuth2Credentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://github.com/login/oauth/authorize")
                        .build(),
                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("https://github.com/login/oauth/access_token")
                        .build(),
                "scope", NodeParameter.builder()
                        .defaultValue("repo user")
                        .build()
        );
    }
}
