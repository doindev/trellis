package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

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
