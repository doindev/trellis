package io.trellis.nodes.core;

import java.util.List;
import java.util.Map;

/**
 * Marker interface for nodes that support engine-level caching.
 * Nodes implementing this will automatically gain cache parameters
 * in their Settings tab and have their execution wrapped with
 * cache lookup/store logic by the WorkflowEngine.
 */
public interface CacheableNode {

	/**
	 * Additional show-conditions to merge into every injected cache parameter's
	 * displayOptions. For example, returning {@code Map.of("operation", List.of("executeQuery"))}
	 * will make all cache settings visible only when the node's "operation" parameter
	 * equals "executeQuery".
	 *
	 * @return extra conditions (keys = param names, values = allowed values), or empty map for none
	 */
	default Map<String, List<Object>> cacheDisplayOptions() {
		return Map.of();
	}
}
