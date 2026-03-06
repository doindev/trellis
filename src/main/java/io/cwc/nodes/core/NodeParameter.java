package io.cwc.nodes.core;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class NodeParameter {
	private String name;
	private String displayName;
	private String description;
	private ParameterType type;
	private Object defaultValue;
	private boolean required;
	private String placeHolder;
	
	// for select/multi select types
	private List<ParameterOption> options;
	
	// for conditional display
	private Map<String, Object> displayOptions;

	// for type-specific options (e.g. rows for string textarea)
	private Map<String, Object> typeOptions;
	
	// parameters with this flag appear in the Settings tab instead of Parameters tab
	private boolean isNodeSetting;

	// for colection types
	private List<NodeParameter> nestedParameters;
	
	// for resource types
	private String resourceType;
	
	// prevents expression mode on this parameter
	private boolean noDataExpression;

	// validation
	private Integer minValue;
	private Integer maxValue;
	private Integer minLength;
	private Integer maxLength;
	private String pattern;
	
	// parameter types
	public enum ParameterType {
		STRING("string"),
		NUMBER("number"),
		BOOLEAN("boolean"),
		OPTIONS("options"),
		MULTI_OPTIONS("multiOptions"),
		COLLECTION("collection"),
		FIXED_COLLECTION("fixedCollection"),
		JSON("json"),
		COLOR("color"),
		DATETIME("datetime"),
		DATE_TIME("dateTime"),
		RESOURCE_LOCATOR("resourceLocator"),
		RESOURCE_MAPPER("resourceMapper"),
		CREDENTIALS("credentials"),
		NOTICE("notice"),
		HIDDEN("hidden");

		private final String jsonValue;

		ParameterType(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		@JsonValue
		public String toJson() {
			return jsonValue;
		}
	}
	
	@Data
	@Builder
	public static class ParameterOption {
		private String name;
		private Object value;
		private String description;
		private String action;
	}
	
	// convert a list of Map's to ParameterOption's
	public static List<ParameterOption> toOptions(List<Map<String, Object>> maps) {
		if (maps == null) {
			return List.of();
		}
		return maps.stream()
			.map(map -> ParameterOption.builder()
				.name((String) map.get("name"))
				.value(map.get("value"))
				.description((String) map.get("description"))
				.action((String) map.get("action"))
				.build())
			.toList();
	}
	
	// custom buildet to support both ParameterOption and map-based options
	public static class NodeParameterBuilder {
		@SuppressWarnings("unchecked")
		public NodeParameterBuilder options(List<?> options) {
			if (options == null || options.isEmpty()) {
				this.options = List.of();
				return this;
			}
			
			Object first = options.get(0);
			if (first instanceof ParameterOption) {
				this.options = (List<ParameterOption>) options;
			} else if (first instanceof Map) {
				this.options = options.stream()
					.map( o -> {
						Map<String, Object> map = (Map<String, Object>) o;
						return ParameterOption.builder()
							.name((String) map.get("name"))
							.value(map.get("value"))
							.description((String) map.get("description"))
							.action((String) map.get("action"))
							.build();
					})
					.toList();
			} else {
				this.options = List.of();
			}
			return this;
		}
	}
}
