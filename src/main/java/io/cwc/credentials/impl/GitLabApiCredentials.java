package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "gitlabApi",
        displayName = "GitLab API",
        description = "GitLab personal access token authentication",
        category = "Developer Tools",
        icon = "gitlab"
)
public class GitLabApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("accessToken").displayName("Personal Access Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("baseUrl").displayName("Base URL")
                        .type(ParameterType.STRING)
                        .defaultValue("https://gitlab.com").build()
        );
    }
}
