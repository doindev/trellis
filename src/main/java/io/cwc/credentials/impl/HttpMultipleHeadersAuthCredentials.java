package io.cwc.credentials.impl;

import java.util.List;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "httpMultipleHeadersAuth",
        displayName = "Http Multiple Headers Auth",
        description = "Http Multiple Headers Auth authentication",
        category = "Generic",
        icon = "httpmultipleheadersauth"
)
public class HttpMultipleHeadersAuthCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("json").displayName("Headers JSON")
                        .type(ParameterType.STRING).required(true).build()
        );
    }
}
