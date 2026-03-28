package io.cwc.nodes.core;

import java.util.ArrayList;
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
	private Map<String, Object> rawParameters;
	private Map<String, Object> credentials;
	private String credentialType;
	private Map<String, Object> staticData;
	private Map<String, Object> workflowStaticData;
	private Map<String, Object> nodeContextData;
	
	private Map<String, List<Object>> aiInputData;
	private Map<String, List<Object>> parentAiInputData;

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
	
	/**
	 * Redacts credential values from a message string, replacing them with masked versions.
	 * Recursively collects all string values from the credentials map (which may contain
	 * nested maps like {name: "apikey", value: "the-secret"}).
	 */
	@SuppressWarnings("unchecked")
	public String redactSecrets(String message) {
		if (message == null || credentials == null || credentials.isEmpty()) return message;
		java.util.Set<String> secrets = new java.util.LinkedHashSet<>();
		collectSecretStrings(credentials, secrets);
		String result = message;
		// Sort by length descending so longer secrets are replaced first (avoids partial matches)
		java.util.List<String> sorted = new java.util.ArrayList<>(secrets);
		sorted.sort((a, b) -> b.length() - a.length());
		for (String secret : sorted) {
			String masked = secret.substring(0, 3) + "***" + secret.substring(secret.length() - 2);
			result = result.replace(secret, masked);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private void collectSecretStrings(Object obj, java.util.Set<String> secrets) {
		if (obj instanceof java.util.Map) {
			for (Object val : ((java.util.Map<?, ?>) obj).values()) {
				collectSecretStrings(val, secrets);
			}
		} else if (obj instanceof java.util.Collection) {
			for (Object item : (java.util.Collection<?>) obj) {
				collectSecretStrings(item, secrets);
			}
		} else if (obj instanceof String s && s.length() >= 8) {
			secrets.add(s);
		} else if (obj != null) {
			String s = String.valueOf(obj);
			if (s.length() >= 8) secrets.add(s);
		}
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
	
	@SuppressWarnings("unchecked")
	public <T> T getAiInput(String connectionType, Class<T> type) {
		if (aiInputData == null) return null;
		List<Object> items = aiInputData.get(connectionType);
		if (items == null || items.isEmpty()) return null;
		Object first = items.get(0);
		if (type.isInstance(first)) return (T) first;
		return null;
	}

	@SuppressWarnings("unchecked")
	public <T> T getParentAiInput(String connectionType, Class<T> type) {
		if (parentAiInputData == null) return null;
		List<Object> items = parentAiInputData.get(connectionType);
		if (items == null || items.isEmpty()) return null;
		Object first = items.get(0);
		if (type.isInstance(first)) return (T) first;
		return null;
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> getAiInputs(String connectionType, Class<T> type) {
		if (aiInputData == null) return List.of();
		List<Object> items = aiInputData.get(connectionType);
		if (items == null) return List.of();
		List<T> result = new ArrayList<>();
		for (Object item : items) {
			if (type.isInstance(item)) result.add((T) item);
		}
		return result;
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
