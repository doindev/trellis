package io.trellis.credentials.impl;

import java.util.List;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

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
