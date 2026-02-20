package io.trellis.credentials;

import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CredentialTypeRegistry {

    private final Map<String, CredentialType> types = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;

    public CredentialTypeRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        discoverProviders();
        resolveInheritance();
        log.info("Credential type registry initialized with {} types", types.size());
    }

    private void discoverProviders() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(CredentialProvider.class);

        for (Object bean : beans.values()) {
            if (!(bean instanceof CredentialProviderInterface provider)) {
                log.warn("Bean {} is annotated with @CredentialProvider but does not implement CredentialProviderInterface",
                        bean.getClass().getName());
                continue;
            }

            CredentialProvider annotation = bean.getClass().getAnnotation(CredentialProvider.class);
            if (annotation == null) {
                continue;
            }

            CredentialType type = CredentialType.builder()
                    .type(annotation.type())
                    .displayName(annotation.displayName())
                    .description(annotation.description())
                    .category(annotation.category())
                    .icon(annotation.icon())
                    .documentationUrl(annotation.documentationUrl())
                    .extendsType(annotation.extendsType().isEmpty() ? null : annotation.extendsType())
                    .properties(new ArrayList<>(provider.getProperties()))
                    .build();

            types.put(type.getType(), type);
            log.debug("Discovered credential provider: {} ({})", annotation.type(), annotation.displayName());
        }
    }

    private void resolveInheritance() {
        // Build provider map for override lookups
        Map<String, CredentialProviderInterface> providerMap = new HashMap<>();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(CredentialProvider.class);
        for (Object bean : beans.values()) {
            if (bean instanceof CredentialProviderInterface provider) {
                CredentialProvider annotation = bean.getClass().getAnnotation(CredentialProvider.class);
                if (annotation != null) {
                    providerMap.put(annotation.type(), provider);
                }
            }
        }

        // Resolve in topological order: parents before children
        Set<String> resolved = new HashSet<>();
        for (CredentialType type : types.values()) {
            resolveType(type, providerMap, resolved, new HashSet<>());
        }
    }

    private void resolveType(CredentialType childType, Map<String, CredentialProviderInterface> providerMap,
                             Set<String> resolved, Set<String> visiting) {
        if (resolved.contains(childType.getType()) || childType.getExtendsType() == null) {
            return;
        }

        // Detect cycles
        if (!visiting.add(childType.getType())) {
            log.warn("Circular inheritance detected for credential type '{}'", childType.getType());
            return;
        }

        CredentialType parentType = types.get(childType.getExtendsType());
        if (parentType == null) {
            log.warn("Credential type '{}' extends unknown type '{}'",
                    childType.getType(), childType.getExtendsType());
            return;
        }

        // Ensure parent is resolved first
        resolveType(parentType, providerMap, resolved, visiting);

        CredentialProviderInterface childProvider = providerMap.get(childType.getType());
        Map<String, NodeParameter> overrides = childProvider != null
                ? childProvider.getPropertyOverrides()
                : Map.of();

        // Build a set of property names the child defines directly
        Set<String> childPropertyNames = new HashSet<>();
        for (NodeParameter p : childType.getProperties()) {
            childPropertyNames.add(p.getName());
        }

        // Merge: start with parent properties (with overrides applied), then append child-only properties
        List<NodeParameter> merged = new ArrayList<>();

        for (NodeParameter parentProp : parentType.getProperties()) {
            if (childPropertyNames.contains(parentProp.getName())) {
                continue;
            }
            if (overrides.containsKey(parentProp.getName())) {
                merged.add(applyOverride(parentProp, overrides.get(parentProp.getName())));
            } else {
                merged.add(parentProp);
            }
        }

        merged.addAll(childType.getProperties());
        childType.setProperties(merged);
        resolved.add(childType.getType());
    }

    private NodeParameter applyOverride(NodeParameter parent, NodeParameter override) {
        NodeParameter.NodeParameterBuilder builder = parent.toBuilder();

        if (override.getType() != null) {
            builder.type(override.getType());
        }
        if (override.getDefaultValue() != null) {
            builder.defaultValue(override.getDefaultValue());
        }
        if (override.getDisplayName() != null) {
            builder.displayName(override.getDisplayName());
        }
        if (override.getDescription() != null) {
            builder.description(override.getDescription());
        }
        if (override.getDisplayOptions() != null) {
            builder.displayOptions(override.getDisplayOptions());
        }
        if (override.getTypeOptions() != null) {
            builder.typeOptions(override.getTypeOptions());
        }
        if (override.getPlaceHolder() != null) {
            builder.placeHolder(override.getPlaceHolder());
        }
        if (override.getOptions() != null) {
            builder.options(override.getOptions());
        }

        return builder.build();
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
