package io.cwc.nodes.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Execute Command Node — executes a system command on the host machine
 * and returns the exit code, stdout, and stderr as output data.
 *
 * Supports running the command once for all items or once per input item.
 */
@Slf4j
@Node(
	type = "executeCommand",
	displayName = "Execute Command",
	description = "Executes a command on the host machine.",
	category = "Core",
	icon = "terminal"
)
public class ExecuteCommandNode extends AbstractNode {

	private static final long DEFAULT_TIMEOUT_MS = 60_000;

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("executeOnce")
				.displayName("Execute Once")
				.description("If active, the command is executed only once instead of once for each input item.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build(),

			NodeParameter.builder()
				.name("command")
				.displayName("Command")
				.description("The command to execute.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("echo \"test\"")
				.typeOptions(Map.of("rows", 5))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null) {
			inputData = List.of();
		}

		boolean executeOnce = toBoolean(context.getParameters().get("executeOnce"), true);
		List<Map<String, Object>> returnData = new ArrayList<>();

		if (executeOnce) {
			// Execute the command once using the first item's context (or no context)
			String command = context.getParameter("command", "");
			try {
				Map<String, Object> result = runCommand(command);
				returnData.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					returnData.add(wrapInJson(Map.of("error", e.getMessage())));
				} else {
					return NodeExecutionResult.error("Command execution failed: " + e.getMessage(), e);
				}
			}
		} else {
			// Execute once per input item
			for (int i = 0; i < inputData.size(); i++) {
				String command = context.getParameter("command", "");
				try {
					Map<String, Object> result = runCommand(command);
					returnData.add(wrapInJson(result));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						returnData.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return NodeExecutionResult.error(
							"Command execution failed for item " + i + ": " + e.getMessage(), e);
					}
				}
			}
		}

		return NodeExecutionResult.success(returnData);
	}

	/**
	 * Executes a system command and captures stdout, stderr, and exit code.
	 */
	private Map<String, Object> runCommand(String command) throws Exception {
		if (command == null || command.isBlank()) {
			throw new IllegalArgumentException("No command specified.");
		}

		ProcessBuilder pb;
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			pb = new ProcessBuilder("cmd.exe", "/c", command);
		} else {
			pb = new ProcessBuilder("/bin/sh", "-c", command);
		}

		pb.redirectErrorStream(false);
		Process process = pb.start();

		// Read stdout and stderr in parallel to avoid deadlocks
		StringBuilder stdout = new StringBuilder();
		StringBuilder stderr = new StringBuilder();

		Thread stdoutReader = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (stdout.length() > 0) stdout.append('\n');
					stdout.append(line);
				}
			} catch (Exception ignored) {
				// stream closed
			}
		});

		Thread stderrReader = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (stderr.length() > 0) stderr.append('\n');
					stderr.append(line);
				}
			} catch (Exception ignored) {
				// stream closed
			}
		});

		stdoutReader.start();
		stderrReader.start();

		boolean completed = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		if (!completed) {
			process.destroyForcibly();
			throw new RuntimeException("Command timed out after " + (DEFAULT_TIMEOUT_MS / 1000) + " seconds.");
		}

		stdoutReader.join(5000);
		stderrReader.join(5000);

		int exitCode = process.exitValue();

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("exitCode", exitCode);
		result.put("stderr", stderr.toString());
		result.put("stdout", stdout.toString());
		return result;
	}
}
