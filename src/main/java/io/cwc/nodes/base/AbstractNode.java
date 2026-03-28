package io.cwc.nodes.base;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInterface;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractNode implements NodeInterface {

	@SuppressWarnings("unchecked")
	protected Object getNestedValue(Map<String, Object> item, String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		
		String actualKey = key.startsWith("json.") ? key.substring(5) : key;
		String[] parts = actualKey.split("\\.");
		Object current = item.get("json");
		if (current == null) {
			current = item;
		}
		
		for (String part : parts) {
			if (current instanceof Map) {
				current = ((Map<String, Object>) current).get(part);
			} else if (current instanceof List) {
				try {
					int index = Integer.parseInt(part);
					current = ((List<?>) current).get(index);
				} catch (NumberFormatException | IndexOutOfBoundsException e) {
					return null;
				}
			} else {
				return null;
			}
			if (current == null) {
				return null;
			}
		}
		return current;
	}
	
	// sets a nested value in a map using dot notation
	@SuppressWarnings("unchecked")
	protected void setNestedValue(Map<String, Object> item, String key, Object value) {
		if (key == null || key.isBlank()) {
			return;
		}
		
		String[] parts = key.split("\\.");
		Map<String, Object> current = item;
		for (int i = 0; i < parts.length -1; i++) {
			String part = parts[i];
			Object next = current.get(part);
			if (!(next instanceof Map)) {
				next = new HashMap<String, Object>();
				current.put(part, next);
			}
			current = (Map<String, Object>) next;
		}
		current.put(parts[parts.length -1], value);
	}
	
	// wrap data in standard format
	protected Map<String, Object> wrapInJson(Object data) {
		Map<String, Object> item = new HashMap<>();
		item.put("json", data);
		return item;
	}
	
	// unwraps from the standard format
	@SuppressWarnings("unchecked")
	protected Map<String, Object> unwrapJson(Map<String, Object> item) {
		Object json = item.get("json");
		if (json instanceof Map) {
			return (Map<String, Object>) json;
		}
		return item;
	}
	
	// deep clones a Map
	@SuppressWarnings("unchecked")
	protected Map<String, Object> deepClone(Map<String, Object> original) {
		Map<String, Object> clone = new HashMap<>();
		for (Map.Entry<String, Object> entry : original.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof Map) {
				clone.put(entry.getKey(), deepClone((Map<String, Object>) value));
			} else if (value instanceof List) {
				clone.put(entry.getKey(), deepCloneList((List<?>) value));
			} else {
				clone.put(entry.getKey(), value);
			}
		}
		return clone;
	}
	
	// deep clones a List
	@SuppressWarnings("unchecked")
	protected List<?> deepCloneList(List<?> original) {
		List<Object> clone = new ArrayList<>();
		for (Object item : original) {
			if (item instanceof Map) {
				clone.add(deepClone((Map<String, Object>) item));
			} else if (item instanceof List) {
				clone.add(deepCloneList((List<?>) item));
			} else {
				clone.add(item);
			}
		}
		return clone;
	}
	
	protected String toString(Object value) {
		return value == null ? "" : String.valueOf(value); 
	}
	
	protected int toInt(Object value, int defaultValue) {
		if (value == null) return defaultValue;
		if (value instanceof Number) return ((Number) value).intValue();
		try {
			return Integer.parseInt(String.valueOf(value));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
	
	protected double toDouble(Object value, double defaultValue) {
		if (value == null) return defaultValue;
		if (value instanceof Number) return ((Number) value).doubleValue();
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
	
	protected boolean toBoolean(Object value, boolean defaultValue) {
		if (value == null) return defaultValue;
		if (value instanceof Boolean) return (Boolean) value;
		String str = String.valueOf(value).toLowerCase();
		return "true".equals(str) || "1".equals(str) || "yes".equals(str);
	}
	
	protected NodeExecutionResult handleError(NodeExecutionContext context, String message, Exception e) {
		String safeMessage = context.redactSecrets(message);
		String safeCause = e != null ? context.redactSecrets(e.getMessage()) : null;
		log.error(safeMessage);
		if (context.isContinueOnFail()) {
			Map<String, Object> errorItem = new HashMap<>();
			errorItem.put("json", Map.of(
					"error", safeMessage,
					"message", safeCause != null ? safeCause : safeMessage
				));
			return NodeExecutionResult.success(List.of(errorItem));
		}
		return NodeExecutionResult.error(safeMessage);
	}
}
