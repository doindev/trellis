package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "serviceNowOAuth2Api",
        displayName = "Service Now O Auth2 A P I",
        description = "Service Now O Auth2 A P I authentication",
        category = "Support",
        icon = "servicenow",
        extendsType = "oAuth2Api"
)
public class ServiceNowOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }
}
