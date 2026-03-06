package io.cwc.credentials.impl;

import java.util.List;
import java.util.Map;

import io.cwc.credentials.CredentialProviderInterface;
import io.cwc.credentials.annotation.CredentialProvider;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

@CredentialProvider(
        type = "microsoftEntraOAuth2Api",
        displayName = "Microsoft Entra ID (Azure AD)",
        description = "App-only (client credentials) authentication for APIs secured by Microsoft Entra ID",
        category = "Cloud Services",
        icon = "microsoft"
)
public class MicrosoftEntraOAuth2ApiCredentials implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("tenantId").displayName("Tenant ID")
                        .type(ParameterType.STRING).required(true)
                        .placeHolder("e.g. xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
                        .description("Azure Portal > Microsoft Entra ID > Overview > Tenant ID")
                        .build(),
                NodeParameter.builder()
                        .name("clientId").displayName("Client ID (Application ID)")
                        .type(ParameterType.STRING).required(true)
                        .placeHolder("e.g. xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
                        .description("Azure Portal > App registrations > your app > Application (client) ID")
                        .build(),
                NodeParameter.builder()
                        .name("clientSecret").displayName("Client Secret")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true))
                        .description("Azure Portal > App registrations > your app > Certificates & secrets")
                        .build(),
                NodeParameter.builder()
                        .name("scope").displayName("Scope")
                        .type(ParameterType.STRING)
                        .defaultValue("https://graph.microsoft.com/.default")
                        .description("For Microsoft Graph use https://graph.microsoft.com/.default — "
                                + "for custom APIs use api://{app-id}/.default")
                        .build(),
                NodeParameter.builder()
                        .name("tokenUrl").displayName("Token Endpoint URL")
                        .type(ParameterType.STRING)
                        .defaultValue("https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token")
                        .description("Replace {tenantId} with your Tenant ID, or leave as-is and it will be resolved automatically")
                        .build()
        );
    }
}
