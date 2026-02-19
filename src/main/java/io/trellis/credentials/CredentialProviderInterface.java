package io.trellis.credentials;

import io.trellis.nodes.core.NodeParameter;

import java.util.List;
import java.util.Map;

/**
 * Interface that all credential providers must implement.
 * Defines the properties (fields) for a credential type.
 */
public interface CredentialProviderInterface {

    /**
     * Returns the list of properties (fields) for this credential type.
     * For child types that extend a parent, these are new or fully-replaced fields.
     */
    List<NodeParameter> getProperties();

    /**
     * Returns partial overrides for inherited parent fields.
     * Keys are property names from the parent; values are NodeParameter objects
     * whose non-null fields will be merged onto the parent property.
     * Common use: set type to HIDDEN, change defaultValue for pre-populated URLs.
     */
    default Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of();
    }
}
