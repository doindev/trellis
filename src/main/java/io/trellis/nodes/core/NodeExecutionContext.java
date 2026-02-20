package io.trellis.nodes.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeExecutionContext {
	private String executionId;
	private String workflowId;
	private String nodeId;
	private String nodeType;
	private int nodeVersion;
	
	private List<Map<String, Object>> inputData;
	private Map<String, Object> parameters;
	private Map<String, Object> credentials;
	private Map<String, Object> staticData;
	private Map<String, Object> workflowStaticData;
	private Map<String, Object> nodeContextData;
	
	private ExecutionMode executionMode;
	private boolean continueOnFail;
	private boolean pairedItem;
	
	public enum ExecutionMode {
		MANUAL,
		TRIGGER,
		WEBHOOK,
		POLLING,
		INTERNAL
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getParameter(String key, T defaultValue) {
		if (parameters == null) {
			return defaultValue;
		}
		Object value = parameters.get(key);
		if (value == null) {
			return defaultValue;
		}
		return (T) value;
	}
	
	public Object getCredential(String key) {
		if (credentials == null) {
			return null;
		}
		return credentials.get(key);
	}
	
	public Map<String, Object> getCredentials() {
		if (credentials == null) {
			return new HashMap<>();
		}
		return credentials;
	}
	
	public Map<String, String> getCredentialsAsStrings() {
		if (credentials == null) {
			return Map.of();
		}
		Map<String, String> result = new HashMap<>();
		for (Map.Entry<String, Object> entry : credentials.entrySet()) {
			if (entry.getValue() != null) {
				result.put(entry.getKey(), String.valueOf(entry.getValue()));
			}
		}
		return result;
	}
	
	public String getCredentialString(String key) {
		return getCredentialString(key, "");
	}
	
	public String getCredentialString(String key, String defaultValue) {
		if (credentials == null) {
			return defaultValue;
		}
		Object value = credentials.get(key);
		return value != null ? String.valueOf(value) : defaultValue;
	}
	
	public boolean isTriggerExecution() {
		return executionMode == ExecutionMode.TRIGGER ||
				executionMode == ExecutionMode.WEBHOOK ||
				executionMode == ExecutionMode.POLLING;
	}
	
	private transient Map<String, Object> currentItem;
	
	public void setCurrentItem(Map<String, Object> item) {
		this.currentItem = item;
	}
	
	public Map<String, Object> getCurrentItem() {
		return currentItem;
	}
	
	public Boolean getParameterAsBoolean(String key) {
		if (parameters == null) {
			return null;
		}
		Object value = parameters.get(key);
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if ( value instanceof String) {
			return Boolean.parseBoolean((String) value);
		}
		return null;
	}
}
