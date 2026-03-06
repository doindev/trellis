package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "datadogApi",
        displayName = "Datadog API",
        description = "Datadog API authentication",
        category = "Analytics",
        icon = "datadog"
)
public class DatadogApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKey").displayName("API Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("applicationKey").displayName("Application Key")
                        .description("Required for most read/write operations.")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build(),
                NodeParameter.builder()
                        .name("site").displayName("Site (Region)")
                        .description("The Datadog site/region for your account.")
                        .type(ParameterType.OPTIONS).required(true)
                        .defaultValue("datadoghq.com")
                        .options(List.of(
                                ParameterOption.builder().name("US1 (datadoghq.com)").value("datadoghq.com").build(),
                                ParameterOption.builder().name("US3 (us3.datadoghq.com)").value("us3.datadoghq.com").build(),
                                ParameterOption.builder().name("US5 (us5.datadoghq.com)").value("us5.datadoghq.com").build(),
                                ParameterOption.builder().name("EU (datadoghq.eu)").value("datadoghq.eu").build(),
                                ParameterOption.builder().name("AP1 (ap1.datadoghq.com)").value("ap1.datadoghq.com").build(),
                                ParameterOption.builder().name("US1-FED (ddog-gov.com)").value("ddog-gov.com").build()
                        )).build()
        );
    }
}
