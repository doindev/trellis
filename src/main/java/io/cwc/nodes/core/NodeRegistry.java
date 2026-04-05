package io.cwc.nodes.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import io.cwc.nodes.annotation.Node;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NodeRegistry {
	private final ApplicationContext applicationContext;
	private final Map<String, NodeRegistration> nodes = new ConcurrentHashMap<>();
	private final Map<String, Map<Integer, NodeRegistration>> versionedNodes = new ConcurrentHashMap<>();
	
	public NodeRegistry(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}
	
	@PostConstruct
	public void init() {
		discoverNodes();
		log.info("Node registry initialized with {} node types", nodes.size());
	}
	
	// discovers and registers all nodes annotated with @Node
	private void discoverNodes() {
		Map<String, Object> nodeBeans = applicationContext.getBeansWithAnnotation(Node.class);
		
		for (Map.Entry<String, Object> entry : nodeBeans.entrySet()) {
			Object bean = entry.getValue();
			if (bean instanceof NodeInterface node) {
				Node annotation = bean.getClass().getAnnotation(Node.class);
				if (annotation != null) {
					registerNode(node, annotation);
				}
			}
		}
	}
	
	// registers a node in the registry
	@SuppressWarnings("unused")
	private void registerNode(NodeInterface node, Node annotation) {
		String type = annotation.type();
		int version = annotation.version();
		String key = type + "_V" + version;

		List<NodeParameter> params = new ArrayList<>(node.getParameters());
		if (node instanceof CacheableNode cacheableNode) {
			params.addAll(getCacheableParameters(cacheableNode.cacheDisplayOptions()));
		}

		NodeRegistration registration = NodeRegistration.builder()
				.type(type)
				.displayName(annotation.displayName())
				.description(annotation.description())
				.category(annotation.category())
				.version(version)
				.icon(annotation.icon())
				.isTrigger(annotation.trigger())
				.isPolling(annotation.polling())
				.credentials(Arrays.asList(annotation.credentials()))
				.group(annotation.group())
				.subtitle(annotation.subtitle())
				.documentationUrl(annotation.documentationUrl())
				.searchOnly(annotation.searchOnly())
				.triggerCategory(annotation.triggerCategory())
				.triggerFavorite(annotation.triggerFavorite())
				.implementationNotes(annotation.implementationNotes())
				.nodeInstance(node)
				.parameters(params)
				.inputs(node.getInputs())
				.outputs(node.getOutputs())
				.build();
		
		nodes.put(key,  registration);
		
		versionedNodes.computeIfAbsent(type,  k -> new ConcurrentHashMap<>()).put(version, registration);
	
		int latestVersion = getLatestVersion(type);
		if (version >= latestVersion) {
			nodes.put(type, registration);
		}
		
		log.debug("Registered node: {} v{} ({})", type, version, annotation.displayName());
	}
	
	// gets a node by type (returns the latest version)
	public Optional<NodeRegistration> getNode(String type) {
		return Optional.ofNullable(nodes.get(type));
	}
	
	// gets a node by type and version
	public Optional<NodeRegistration> getNode(String type, int version) {
		return Optional.ofNullable(nodes.get(type + "_V" + version));
	}
	
	// gets all available versions of a node
	public List<Integer> getVersions(String type) {
		Map<Integer, NodeRegistration> versions = versionedNodes.get(type);
		if (versions == null) {
			return List.of();
		}
		return new ArrayList<>(versions.keySet()).stream()
				.sorted()
				.collect(Collectors.toList());
	}
	
	// get the latest version number of a node type
	public int getLatestVersion(String type) {
		return getVersions(type).stream()
			.max(Integer::compare)
			.orElse(1);
	}
	
	// get all registered nodes
	public Collection<NodeRegistration> getAllNodes() {
		return nodes.values().stream()
				.filter(n -> !n.getType().contains("_V"))
				.collect(Collectors.toSet());
	}
	
	// get all trigger nodes
	public Collection<NodeRegistration> getTriggerNodes() {
		return getAllNodes().stream()
				.filter(NodeRegistration::isTrigger)
				.collect(Collectors.toList());
	}
	
	// get nodes by category
	public Collection<NodeRegistration> getNodesByCategory(String category) {
		return getAllNodes().stream()
				.filter(n -> category.equalsIgnoreCase(n.getCategory()))
				.collect(Collectors.toList());
	}

	// get category names with node counts
	public Map<String, Long> getCategoriesWithCounts() {
		return getAllNodes().stream()
				.collect(Collectors.groupingBy(NodeRegistration::getCategory, Collectors.counting()));
	}
	
	
	// returns cache parameters injected into CacheableNode implementations
	private List<NodeParameter> getCacheableParameters(Map<String, List<Object>> extraShowConditions) {
		return List.of(
			NodeParameter.builder()
				.name("cacheEnabled")
				.displayName("Caching Enabled")
				.description("Enable engine-level caching for this node.")
				.type(NodeParameter.ParameterType.BOOLEAN)
				.defaultValue(false)
				.isNodeSetting(true)
				.noDataExpression(true)
				.displayOptions(buildCacheDisplayOptions(Map.of(), extraShowConditions))
				.build(),
			NodeParameter.builder()
				.name("cacheSource")
				.displayName("Cache Source")
				.description("Use a pre-defined cache or configure one inline.")
				.type(NodeParameter.ParameterType.OPTIONS)
				.defaultValue("inline")
				.isNodeSetting(true)
				.noDataExpression(true)
				.options(List.of(
					NodeParameter.ParameterOption.builder().name("Inline Configuration").value("inline").build(),
					NodeParameter.ParameterOption.builder().name("Select Defined Cache").value("select").build()
				))
				.displayOptions(buildCacheDisplayOptions(Map.of("cacheEnabled", List.of(true)), extraShowConditions))
				.build(),
			NodeParameter.builder()
				.name("cacheName")
				.displayName("Cache Name")
				.description("Name of the cache to use.")
				.type(NodeParameter.ParameterType.STRING)
				.defaultValue("")
				.isNodeSetting(true)
				.displayOptions(buildCacheDisplayOptions(Map.of("cacheEnabled", List.of(true)), extraShowConditions))
				.build(),
			NodeParameter.builder()
				.name("cacheMaxSize")
				.displayName("Max Entries")
				.description("Maximum number of entries in the cache.")
				.type(NodeParameter.ParameterType.NUMBER)
				.defaultValue(1000)
				.isNodeSetting(true)
				.displayOptions(buildCacheDisplayOptions(Map.of("cacheEnabled", List.of(true), "cacheSource", List.of("inline")), extraShowConditions))
				.build(),
			NodeParameter.builder()
				.name("cacheTtlSeconds")
				.displayName("TTL (seconds)")
				.description("Time-to-live for cache entries in seconds.")
				.type(NodeParameter.ParameterType.NUMBER)
				.defaultValue(3600)
				.isNodeSetting(true)
				.displayOptions(buildCacheDisplayOptions(Map.of("cacheEnabled", List.of(true), "cacheSource", List.of("inline")), extraShowConditions))
				.build(),
			NodeParameter.builder()
				.name("cacheKey")
				.displayName("Cache Key")
				.description("The cache key. Use a field path (e.g. 'url') or expression.")
				.type(NodeParameter.ParameterType.STRING)
				.defaultValue("")
				.isNodeSetting(true)
				.displayOptions(buildCacheDisplayOptions(Map.of("cacheEnabled", List.of(true)), extraShowConditions))
				.build()
		);
	}

	// merges base show-conditions with extra per-node conditions into a displayOptions map
	private Map<String, Object> buildCacheDisplayOptions(Map<String, Object> baseShow,
			Map<String, List<Object>> extraShowConditions) {
		if (baseShow.isEmpty() && (extraShowConditions == null || extraShowConditions.isEmpty())) {
			return null;
		}
		Map<String, Object> merged = new java.util.LinkedHashMap<>(baseShow);
		if (extraShowConditions != null) {
			merged.putAll(extraShowConditions);
		}
		return Map.of("show", merged);
	}

	@Data
	@Builder
	public static class NodeRegistration {
		private String type;
		private String displayName;
		private String description;
		private String category;
		private int version;
		private String icon;
		private boolean isTrigger;
		private boolean isPolling;
		private List<String> credentials;
		private String group;
		private String subtitle;
		private String documentationUrl;
		private NodeInterface nodeInstance;
		private boolean searchOnly;
		private String triggerCategory;
		private boolean triggerFavorite;
		private String implementationNotes;
		private List<NodeParameter> parameters;
		private List<NodeInput> inputs;
		private List<NodeOutput> outputs;
	}
}
