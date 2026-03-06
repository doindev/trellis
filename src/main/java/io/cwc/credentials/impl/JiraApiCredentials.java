package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "jiraApi",
        displayName = "Jira API",
        description = "Jira Cloud API authentication",
        category = "Developer Tools",
        icon = "jira"
)
public class JiraApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("email").displayName("Email")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("apiToken").displayName("API Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("domain").displayName("Domain")
                        .type(ParameterType.STRING).required(true)
                        .placeHolder("your-domain.atlassian.net").build()
        );
    }
}
