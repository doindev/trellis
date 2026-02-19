package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "mauticOAuth2Api",
        displayName = "Mautic O Auth2 A P I",
        description = "Mautic O Auth2 A P I authentication",
        category = "CRM / Sales",
        icon = "mautic",
        extendsType = "oAuth2Api"
)
public class MauticOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }
}
