package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

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
