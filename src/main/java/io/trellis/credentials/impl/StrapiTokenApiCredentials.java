package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "strapiTokenApi",
        displayName = "Strapi Token API",
        description = "Strapi Token API authentication",
        category = "Productivity",
        icon = "strapitoken"
)
public class StrapiTokenApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiToken").displayName("API Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("url").displayName("URL")
                        .type(ParameterType.STRING)
                        .defaultValue("http://localhost:1337").build()
        );
    }
}
