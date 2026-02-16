package io.trellis.credentials;

import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CredentialTypeRegistry {

    private final Map<String, CredentialType> types = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        registerBuiltinTypes();
        log.info("Credential type registry initialized with {} types", types.size());
    }

    private void registerBuiltinTypes() {
        register(CredentialType.builder()
                .type("httpBasicAuth")
                .displayName("HTTP Basic Auth")
                .description("Basic authentication with username and password")
                .properties(List.of(
                        NodeParameter.builder().name("username").displayName("Username").type(ParameterType.STRING).required(true).build(),
                        NodeParameter.builder().name("password").displayName("Password").type(ParameterType.STRING).required(true)
                                .typeOptions(Map.of("password", true)).build()
                ))
                .build());

        register(CredentialType.builder()
                .type("httpHeaderAuth")
                .displayName("HTTP Header Auth")
                .description("Authentication via custom HTTP header")
                .properties(List.of(
                        NodeParameter.builder().name("name").displayName("Header Name").type(ParameterType.STRING).required(true)
                                .defaultValue("Authorization").build(),
                        NodeParameter.builder().name("value").displayName("Header Value").type(ParameterType.STRING).required(true)
                                .typeOptions(Map.of("password", true)).build()
                ))
                .build());

        register(CredentialType.builder()
                .type("httpQueryAuth")
                .displayName("HTTP Query Auth")
                .description("Authentication via query parameter")
                .properties(List.of(
                        NodeParameter.builder().name("name").displayName("Parameter Name").type(ParameterType.STRING).required(true).build(),
                        NodeParameter.builder().name("value").displayName("Parameter Value").type(ParameterType.STRING).required(true)
                                .typeOptions(Map.of("password", true)).build()
                ))
                .build());

        register(CredentialType.builder()
                .type("oAuth2Api")
                .displayName("OAuth2 API")
                .description("OAuth2 authentication")
                .properties(List.of(
                        NodeParameter.builder().name("grantType").displayName("Grant Type").type(ParameterType.OPTIONS)
                                .options(List.of(
                                        NodeParameter.ParameterOption.builder().name("Authorization Code").value("authorizationCode").build(),
                                        NodeParameter.ParameterOption.builder().name("Client Credentials").value("clientCredentials").build()
                                ))
                                .defaultValue("authorizationCode").build(),
                        NodeParameter.builder().name("authorizationUrl").displayName("Authorization URL").type(ParameterType.STRING).build(),
                        NodeParameter.builder().name("accessTokenUrl").displayName("Access Token URL").type(ParameterType.STRING).required(true).build(),
                        NodeParameter.builder().name("clientId").displayName("Client ID").type(ParameterType.STRING).required(true).build(),
                        NodeParameter.builder().name("clientSecret").displayName("Client Secret").type(ParameterType.STRING).required(true)
                                .typeOptions(Map.of("password", true)).build(),
                        NodeParameter.builder().name("scope").displayName("Scope").type(ParameterType.STRING).build(),
                        NodeParameter.builder().name("accessToken").displayName("Access Token").type(ParameterType.STRING)
                                .typeOptions(Map.of("password", true)).build()
                ))
                .build());
    }

    public void register(CredentialType type) {
        types.put(type.getType(), type);
    }

    public Optional<CredentialType> getType(String type) {
        return Optional.ofNullable(types.get(type));
    }

    public Collection<CredentialType> getAllTypes() {
        return types.values();
    }
}
