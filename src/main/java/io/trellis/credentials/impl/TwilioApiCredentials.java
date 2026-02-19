package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "twilioApi",
        displayName = "Twilio API",
        description = "Twilio API authentication",
        category = "Communication",
        icon = "twilio"
)
public class TwilioApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("accountSid").displayName("Account SID")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("authToken").displayName("Auth Token")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
