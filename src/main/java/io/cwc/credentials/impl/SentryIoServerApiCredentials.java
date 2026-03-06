package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "sentryIoServerApi",
        displayName = "Sentry.io Server API",
        description = "Sentry.io Server API authentication",
        category = "Support",
        icon = "sentryioserver"
)
public class SentryIoServerApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("url").displayName("Sentry Server URL")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("apiToken").displayName("API Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
