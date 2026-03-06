package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "slackApi",
        displayName = "Slack API",
        description = "Slack Bot Token authentication",
        category = "Communication",
        icon = "slack"
)
public class SlackApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("accessToken").displayName("Bot User OAuth Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
