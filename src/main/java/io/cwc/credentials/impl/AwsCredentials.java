package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "awsApi",
        displayName = "AWS API",
        description = "Amazon Web Services authentication",
        category = "Cloud Services",
        icon = "aws"
)
public class AwsCredentials implements CredentialProviderInterface {

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
                        .name("region").displayName("Region")
                        .type(ParameterType.OPTIONS)
                        .defaultValue("us-east-1")
                        .options(List.of(
                                NodeParameter.ParameterOption.builder().name("US East (N. Virginia)").value("us-east-1").build(),
                                NodeParameter.ParameterOption.builder().name("US East (Ohio)").value("us-east-2").build(),
                                NodeParameter.ParameterOption.builder().name("US West (N. California)").value("us-west-1").build(),
                                NodeParameter.ParameterOption.builder().name("US West (Oregon)").value("us-west-2").build(),
                                NodeParameter.ParameterOption.builder().name("EU (Ireland)").value("eu-west-1").build(),
                                NodeParameter.ParameterOption.builder().name("EU (London)").value("eu-west-2").build(),
                                NodeParameter.ParameterOption.builder().name("EU (Frankfurt)").value("eu-central-1").build(),
                                NodeParameter.ParameterOption.builder().name("Asia Pacific (Tokyo)").value("ap-northeast-1").build(),
                                NodeParameter.ParameterOption.builder().name("Asia Pacific (Sydney)").value("ap-southeast-2").build(),
                                NodeParameter.ParameterOption.builder().name("Asia Pacific (Singapore)").value("ap-southeast-1").build()
                        )).build()
        );
    }
}
