package io.trellis.nodes.impl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * SSH Node -- executes commands on remote servers, uploads and downloads files via SSH/SCP.
 * Uses the system ssh and scp commands via ProcessBuilder to avoid additional library dependencies.
 */
@Slf4j
@Node(
	type = "ssh",
	displayName = "SSH",
	description = "Execute commands and transfer files on remote servers via SSH.",
	category = "File Operations",
	icon = "ssh",
	credentials = {"sshApi"}
)
public class SshNode extends AbstractNode {

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
		List<NodeParameter> params = new ArrayList<>();

		// Operation selector
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("command")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Execute Command").value("command").description("Execute a command on the remote server").build(),
				ParameterOption.builder().name("Download File").value("download").description("Download a file from the remote server").build(),
				ParameterOption.builder().name("Upload File").value("upload").description("Upload a file to the remote server").build()
			)).build());

		// Authentication type
		params.add(NodeParameter.builder()
			.name("authType").displayName("Authentication")
			.type(ParameterType.OPTIONS).defaultValue("password")
			.options(List.of(
				ParameterOption.builder().name("Password").value("password").build(),
				ParameterOption.builder().name("Private Key").value("privateKey").build()
			)).build());

		// Command parameter
		params.add(NodeParameter.builder()
			.name("command").displayName("Command")
			.type(ParameterType.STRING).required(true)
			.description("The command to execute on the remote server.")
			.placeHolder("ls -la /home")
			.displayOptions(Map.of("show", Map.of("operation", List.of("command"))))
			.build());

		// Working directory
		params.add(NodeParameter.builder()
			.name("cwd").displayName("Working Directory")
			.type(ParameterType.STRING).defaultValue("")
			.description("Working directory for the command execution.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("command"))))
			.build());

		// Download parameters
		params.add(NodeParameter.builder()
			.name("remotePath").displayName("Remote File Path")
			.type(ParameterType.STRING).required(true)
			.description("Path to the file on the remote server.")
			.placeHolder("/home/user/file.txt")
			.displayOptions(Map.of("show", Map.of("operation", List.of("download", "upload"))))
			.build());

		params.add(NodeParameter.builder()
			.name("localPath").displayName("Local File Path")
			.type(ParameterType.STRING).required(true)
			.description("Path on the local machine.")
			.placeHolder("/tmp/file.txt")
			.displayOptions(Map.of("show", Map.of("operation", List.of("download", "upload"))))
			.build());

		// Options
		params.add(NodeParameter.builder()
			.name("sshOptions").displayName("Options")
			.type(ParameterType.COLLECTION)
			.nestedParameters(List.of(
				NodeParameter.builder().name("port").displayName("Port").type(ParameterType.NUMBER)
					.defaultValue(22).description("SSH port number.").build(),
				NodeParameter.builder().name("timeout").displayName("Timeout (seconds)").type(ParameterType.NUMBER)
					.defaultValue(30).description("Connection timeout in seconds.").build(),
				NodeParameter.builder().name("strictHostKeyChecking").displayName("Strict Host Key Checking")
					.type(ParameterType.BOOLEAN).defaultValue(false)
					.description("Enable strict host key checking.").build()
			)).build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "command");
		Map<String, Object> credentials = context.getCredentials();

		String host = String.valueOf(credentials.getOrDefault("host", ""));
		String username = String.valueOf(credentials.getOrDefault("username", ""));
		String password = String.valueOf(credentials.getOrDefault("password", ""));
		String privateKey = String.valueOf(credentials.getOrDefault("privateKey", ""));
		String authType = context.getParameter("authType", "password");

		// Get options
		Map<String, Object> options = context.getParameter("sshOptions", Map.of());
		int port = toInt(options.getOrDefault("port", 22), 22);
		int timeout = toInt(options.getOrDefault("timeout", 30), 30);
		boolean strictHostKey = toBoolean(options.getOrDefault("strictHostKeyChecking", false), false);

		if (host.isEmpty() || username.isEmpty()) {
			return NodeExecutionResult.error("SSH credentials (host and username) are required.");
		}

		try {
			return switch (operation) {
				case "command" -> executeCommand(context, host, username, password, privateKey, authType, port, timeout, strictHostKey);
				case "download" -> executeDownload(context, host, username, password, privateKey, authType, port, timeout, strictHostKey);
				case "upload" -> executeUpload(context, host, username, password, privateKey, authType, port, timeout, strictHostKey);
				default -> NodeExecutionResult.error("Unknown operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "SSH error: " + e.getMessage(), e);
		}
	}

	// ========================= Command =========================

	private NodeExecutionResult executeCommand(NodeExecutionContext context, String host, String username,
			String password, String privateKey, String authType, int port, int timeout, boolean strictHostKey) throws Exception {

		String command = context.getParameter("command", "");
		String cwd = context.getParameter("cwd", "");

		if (command.isEmpty()) {
			return NodeExecutionResult.error("Command is required.");
		}

		// Build the full command with optional cd
		String fullCommand = cwd.isEmpty() ? command : "cd " + cwd + " && " + command;

		List<String> sshCommand = buildSshCommand(host, username, password, privateKey, authType, port, timeout, strictHostKey);
		sshCommand.add(fullCommand);

		ProcessResult result = runProcess(sshCommand, timeout);

		Map<String, Object> resultData = new LinkedHashMap<>();
		resultData.put("stdout", result.stdout);
		resultData.put("stderr", result.stderr);
		resultData.put("exitCode", result.exitCode);
		resultData.put("command", command);
		resultData.put("host", host);

		return NodeExecutionResult.success(List.of(wrapInJson(resultData)));
	}

	// ========================= Download =========================

	private NodeExecutionResult executeDownload(NodeExecutionContext context, String host, String username,
			String password, String privateKey, String authType, int port, int timeout, boolean strictHostKey) throws Exception {

		String remotePath = context.getParameter("remotePath", "");
		String localPath = context.getParameter("localPath", "");

		if (remotePath.isEmpty() || localPath.isEmpty()) {
			return NodeExecutionResult.error("Remote path and local path are required.");
		}

		List<String> scpCommand = buildScpCommand(host, username, password, privateKey, authType, port, timeout, strictHostKey);
		scpCommand.add(username + "@" + host + ":" + remotePath);
		scpCommand.add(localPath);

		ProcessResult result = runProcess(scpCommand, timeout);

		Map<String, Object> resultData = new LinkedHashMap<>();
		resultData.put("success", result.exitCode == 0);
		resultData.put("remotePath", remotePath);
		resultData.put("localPath", localPath);
		resultData.put("exitCode", result.exitCode);
		if (result.exitCode != 0) {
			resultData.put("error", result.stderr);
		} else {
			// Include file info
			Path local = Path.of(localPath);
			if (Files.exists(local)) {
				resultData.put("fileSize", Files.size(local));
				resultData.put("fileName", local.getFileName().toString());
			}
		}

		return NodeExecutionResult.success(List.of(wrapInJson(resultData)));
	}

	// ========================= Upload =========================

	private NodeExecutionResult executeUpload(NodeExecutionContext context, String host, String username,
			String password, String privateKey, String authType, int port, int timeout, boolean strictHostKey) throws Exception {

		String remotePath = context.getParameter("remotePath", "");
		String localPath = context.getParameter("localPath", "");

		if (remotePath.isEmpty() || localPath.isEmpty()) {
			return NodeExecutionResult.error("Remote path and local path are required.");
		}

		if (!Files.exists(Path.of(localPath))) {
			return NodeExecutionResult.error("Local file does not exist: " + localPath);
		}

		List<String> scpCommand = buildScpCommand(host, username, password, privateKey, authType, port, timeout, strictHostKey);
		scpCommand.add(localPath);
		scpCommand.add(username + "@" + host + ":" + remotePath);

		ProcessResult result = runProcess(scpCommand, timeout);

		Map<String, Object> resultData = new LinkedHashMap<>();
		resultData.put("success", result.exitCode == 0);
		resultData.put("remotePath", remotePath);
		resultData.put("localPath", localPath);
		resultData.put("exitCode", result.exitCode);
		if (result.exitCode != 0) {
			resultData.put("error", result.stderr);
		} else {
			resultData.put("fileSize", Files.size(Path.of(localPath)));
		}

		return NodeExecutionResult.success(List.of(wrapInJson(resultData)));
	}

	// ========================= Helpers =========================

	private List<String> buildSshCommand(String host, String username, String password, String privateKey,
			String authType, int port, int timeout, boolean strictHostKey) throws IOException {

		List<String> cmd = new ArrayList<>();
		cmd.add("ssh");

		// Port
		cmd.add("-p");
		cmd.add(String.valueOf(port));

		// Connection timeout
		cmd.add("-o");
		cmd.add("ConnectTimeout=" + timeout);

		// Host key checking
		if (!strictHostKey) {
			cmd.add("-o");
			cmd.add("StrictHostKeyChecking=no");
			cmd.add("-o");
			cmd.add("UserKnownHostsFile=/dev/null");
		}

		// Disable password prompt if using key auth
		cmd.add("-o");
		cmd.add("BatchMode=yes");

		// Authentication
		if ("privateKey".equals(authType) && !privateKey.isEmpty()) {
			Path keyFile = createTempKeyFile(privateKey);
			cmd.add("-i");
			cmd.add(keyFile.toString());
		}

		cmd.add(username + "@" + host);
		return cmd;
	}

	private List<String> buildScpCommand(String host, String username, String password, String privateKey,
			String authType, int port, int timeout, boolean strictHostKey) throws IOException {

		List<String> cmd = new ArrayList<>();
		cmd.add("scp");

		// Port
		cmd.add("-P");
		cmd.add(String.valueOf(port));

		// Connection timeout
		cmd.add("-o");
		cmd.add("ConnectTimeout=" + timeout);

		// Host key checking
		if (!strictHostKey) {
			cmd.add("-o");
			cmd.add("StrictHostKeyChecking=no");
			cmd.add("-o");
			cmd.add("UserKnownHostsFile=/dev/null");
		}

		// Disable password prompt
		cmd.add("-o");
		cmd.add("BatchMode=yes");

		// Authentication
		if ("privateKey".equals(authType) && !privateKey.isEmpty()) {
			Path keyFile = createTempKeyFile(privateKey);
			cmd.add("-i");
			cmd.add(keyFile.toString());
		}

		return cmd;
	}

	private Path createTempKeyFile(String privateKey) throws IOException {
		Path tempFile = Files.createTempFile("trellis_ssh_key_", ".pem");
		Files.writeString(tempFile, privateKey);
		// Set permissions (Unix only, will be ignored on Windows)
		try {
			tempFile.toFile().setReadable(false, false);
			tempFile.toFile().setReadable(true, true);
			tempFile.toFile().setWritable(false, false);
			tempFile.toFile().setWritable(true, true);
		} catch (Exception e) {
			log.debug("Could not set key file permissions (non-Unix OS): {}", e.getMessage());
		}
		tempFile.toFile().deleteOnExit();
		return tempFile;
	}

	private ProcessResult runProcess(List<String> command, int timeoutSeconds) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.redirectErrorStream(false);
		Process process = pb.start();

		String stdout;
		String stderr;
		try (
			BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
			BufferedReader stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))
		) {
			boolean completed = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
			if (!completed) {
				process.destroyForcibly();
				return new ProcessResult("", "Process timed out after " + timeoutSeconds + " seconds", -1);
			}

			StringBuilder stdoutSb = new StringBuilder();
			String line;
			while ((line = stdoutReader.readLine()) != null) {
				if (stdoutSb.length() > 0) stdoutSb.append("\n");
				stdoutSb.append(line);
			}
			stdout = stdoutSb.toString();

			StringBuilder stderrSb = new StringBuilder();
			while ((line = stderrReader.readLine()) != null) {
				if (stderrSb.length() > 0) stderrSb.append("\n");
				stderrSb.append(line);
			}
			stderr = stderrSb.toString();
		}

		return new ProcessResult(stdout, stderr, process.exitValue());
	}

	private static class ProcessResult {
		final String stdout;
		final String stderr;
		final int exitCode;

		ProcessResult(String stdout, String stderr, int exitCode) {
			this.stdout = stdout;
			this.stderr = stderr;
			this.exitCode = exitCode;
		}
	}
}
