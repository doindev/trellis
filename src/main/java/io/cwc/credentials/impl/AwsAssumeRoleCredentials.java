package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "awsAssumeRole",
        displayName = "Aws Assume Role",
        description = "Aws Assume Role authentication",
        category = "Cloud Services",
        icon = "awsassumerole"
)
public class AwsAssumeRoleCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("accessKeyId").displayName("Access Key ID")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("secretAccessKey").displayName("Secret Access Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("roleArn").displayName("Role ARN")
                        .type(ParameterType.STRING).required(true).build(),
                NodeParameter.builder()
                        .name("region").displayName("Region")
                        .type(ParameterType.STRING)
                        .defaultValue("us-east-1").build()
        );
    }
}
