package io.cwc.nodes.core;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NodeExecutionResult {
	// output data per output connection
	// list index corresponds to output connection index
	private List<List<Map<String, Object>>> output;
	
	// any binary data produced by the node
	private Map<String, BinaryData> binaryData;
	
	// error that occurred during execution if any
	private NodeExecutionError error;
	
	// execution hints/warnings (non-fatal)
	private List<String> hints;
	
	// should execution continue to next nodes
	@Builder.Default
	private boolean continueExecution = true;
	
	// static data to persist between executions
	private Map<String, Object> staticData;

	// wait configuration — when set, the engine checkpoints state and releases the thread
	private WaitConfig waitConfig;
	
	// creates a successful result with a single output
	public static NodeExecutionResult success(List<Map<String, Object>> items) {
		return NodeExecutionResult.builder()
				.output(List.of(items))
				.build();
	}
	
	// creates a successful result with multiple outputs
	public static NodeExecutionResult successMultiOutput(List<List<Map<String, Object>>> outputs) {
		return NodeExecutionResult.builder()
				.output(outputs)
				.build();
	}
	
	// creates an error result
	public static NodeExecutionResult error(String message, Exception cause) {
		return NodeExecutionResult.builder()
				.error(NodeExecutionError.builder()
					.message(message)
					.cause(cause != null ? cause.getMessage() :  null)
					.build())
				.continueExecution(false)
				.build();
	}
	
	// create error result without exception
	public static NodeExecutionResult error(String message) {
		return error(message, null);
	}
	
	// create an empty result (no items to output)
	public static NodeExecutionResult empty() {
		return NodeExecutionResult.builder()
				.output(List.of(List.of()))
				.build();
	}

	// create a waiting result — the engine will checkpoint state and release the thread
	public static NodeExecutionResult waiting(WaitConfig config) {
		return NodeExecutionResult.builder()
				.waitConfig(config)
				.continueExecution(false)
				.build();
	}
	
	@Data
	@Builder
	public static class BinaryData {
		private String id;
		private String mimeType;
		private String fileName;
		private long fileSize;
		private String storageLocation; // "db", "fs", "s3"
	}
	
	@Data
	@Builder
	public static class WaitConfig {
		private String waitType;       // "form", "webhook", "timeInterval", "specificTime"
		private Instant resumeAt;      // for time-based waits
		private Object formDefinition; // for form waits
	}

	@Data
	@Builder
	public static class NodeExecutionError {
		private String message;
		private String cause;
		private String node;
		private String itemIndex;
		private Map<String, Object> context;
	}
}
