package io.trellis.nodes.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
 * Git Node -- performs local Git operations using ProcessBuilder to execute
 * git commands on the host filesystem.
 */
@Slf4j
@Node(
	type = "git",
	displayName = "Git",
	description = "Perform local Git operations such as clone, commit, push, pull, and more",
	category = "Development",
	icon = "git",
	credentials = {}
)
public class GitNode extends AbstractNode {

	private static final long DEFAULT_TIMEOUT_MS = 120_000;

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("status")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Add").value("add")
					.description("Add file contents to the staging area").build(),
				ParameterOption.builder().name("Add Config").value("addConfig")
					.description("Add a git configuration entry").build(),
				ParameterOption.builder().name("Clone").value("clone")
					.description("Clone a repository into a new directory").build(),
				ParameterOption.builder().name("Commit").value("commit")
					.description("Record changes to the repository").build(),
				ParameterOption.builder().name("Fetch").value("fetch")
					.description("Download objects and refs from a remote").build(),
				ParameterOption.builder().name("Log").value("log")
					.description("Show commit logs").build(),
				ParameterOption.builder().name("Pull").value("pull")
					.description("Fetch from and integrate with a remote").build(),
				ParameterOption.builder().name("Push").value("push")
					.description("Update remote refs along with associated objects").build(),
				ParameterOption.builder().name("Push Tags").value("pushTags")
					.description("Push all tags to remote").build(),
				ParameterOption.builder().name("Status").value("status")
					.description("Show the working tree status").build(),
				ParameterOption.builder().name("Tag").value("tag")
					.description("Create a tag").build()
			)).build());

		// Repository path (used by all operations except clone)
		params.add(NodeParameter.builder()
			.name("repositoryPath").displayName("Repository Path")
			.type(ParameterType.STRING).required(true)
			.description("The local path to the Git repository.")
			.placeHolder("/home/user/my-project")
			.build());

		// Clone parameters
		params.add(NodeParameter.builder()
			.name("sourceRepo").displayName("Source Repository")
			.type(ParameterType.STRING).required(true)
			.description("The URL or path of the repository to clone.")
			.placeHolder("https://github.com/user/repo.git")
			.displayOptions(Map.of("show", Map.of("operation", List.of("clone"))))
			.build());

		params.add(NodeParameter.builder()
			.name("directory").displayName("Directory")
			.type(ParameterType.STRING)
			.description("The name of a new directory to clone into. If empty, the repository name is used.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("clone"))))
			.build());

		// Add parameters
		params.add(NodeParameter.builder()
			.name("pathspec").displayName("Paths to Add")
			.type(ParameterType.STRING)
			.defaultValue(".")
			.description("Files to add to the staging area. Use '.' for all files.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("add"))))
			.build());

		// Add Config parameters
		params.add(NodeParameter.builder()
			.name("key").displayName("Key")
			.type(ParameterType.STRING).required(true)
			.description("The configuration key (e.g. user.name, user.email).")
			.placeHolder("user.email")
			.displayOptions(Map.of("show", Map.of("operation", List.of("addConfig"))))
			.build());

		params.add(NodeParameter.builder()
			.name("value").displayName("Value")
			.type(ParameterType.STRING).required(true)
			.description("The configuration value.")
			.placeHolder("user@example.com")
			.displayOptions(Map.of("show", Map.of("operation", List.of("addConfig"))))
			.build());

		// Commit parameters
		params.add(NodeParameter.builder()
			.name("message").displayName("Message")
			.type(ParameterType.STRING).required(true)
			.description("The commit message.")
			.placeHolder("Initial commit")
			.displayOptions(Map.of("show", Map.of("operation", List.of("commit"))))
			.build());

		// Push/Pull/Fetch parameters
		params.add(NodeParameter.builder()
			.name("remote").displayName("Remote")
			.type(ParameterType.STRING)
			.defaultValue("origin")
			.description("The name of the remote.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("push", "pull", "fetch"))))
			.build());

		params.add(NodeParameter.builder()
			.name("branch").displayName("Branch")
			.type(ParameterType.STRING)
			.description("The branch name. If empty, the current branch is used.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("push", "pull", "fetch"))))
			.build());

		// Push Tags parameters
		params.add(NodeParameter.builder()
			.name("pushTagsRemote").displayName("Remote")
			.type(ParameterType.STRING)
			.defaultValue("origin")
			.description("The name of the remote to push tags to.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("pushTags"))))
			.build());

		// Tag parameters
		params.add(NodeParameter.builder()
			.name("name").displayName("Tag Name")
			.type(ParameterType.STRING).required(true)
			.description("The name of the tag to create.")
			.placeHolder("v1.0.0")
			.displayOptions(Map.of("show", Map.of("operation", List.of("tag"))))
			.build());

		// Log parameters
		params.add(NodeParameter.builder()
			.name("returnAll").displayName("Return All")
			.type(ParameterType.BOOLEAN)
			.defaultValue(false)
			.description("Whether to return all log entries.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("log"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER)
			.defaultValue(10)
			.description("Max number of log entries to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("log"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "status");
		String repositoryPath = context.getParameter("repositoryPath", "");

		try {
			Map<String, Object> result = switch (operation) {
				case "add" -> executeAdd(context, repositoryPath);
				case "addConfig" -> executeAddConfig(context, repositoryPath);
				case "clone" -> executeClone(context, repositoryPath);
				case "commit" -> executeCommit(context, repositoryPath);
				case "fetch" -> executeFetch(context, repositoryPath);
				case "log" -> executeLog(context, repositoryPath);
				case "pull" -> executePull(context, repositoryPath);
				case "push" -> executePush(context, repositoryPath);
				case "pushTags" -> executePushTags(context, repositoryPath);
				case "status" -> executeStatus(repositoryPath);
				case "tag" -> executeTag(context, repositoryPath);
				default -> Map.of("error", (Object) ("Unknown operation: " + operation));
			};

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Git operation '" + operation + "' failed: " + e.getMessage(), e);
		}
	}

	// ========================= Operations =========================

	private Map<String, Object> executeAdd(NodeExecutionContext context, String repoPath) throws Exception {
		String pathspec = context.getParameter("pathspec", ".");
		List<String> command = new ArrayList<>(List.of("git", "add"));
		command.addAll(Arrays.asList(pathspec.split("\\s+")));
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executeAddConfig(NodeExecutionContext context, String repoPath) throws Exception {
		String key = context.getParameter("key", "");
		String value = context.getParameter("value", "");
		List<String> command = List.of("git", "config", key, value);
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executeClone(NodeExecutionContext context, String repoPath) throws Exception {
		String sourceRepo = context.getParameter("sourceRepo", "");
		String directory = context.getParameter("directory", "");

		List<String> command = new ArrayList<>(List.of("git", "clone", sourceRepo));
		if (directory != null && !directory.isEmpty()) {
			command.add(directory);
		}

		// Clone runs in the parent directory (repositoryPath is the target parent)
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executeCommit(NodeExecutionContext context, String repoPath) throws Exception {
		String message = context.getParameter("message", "");
		List<String> command = List.of("git", "commit", "-m", message);
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executeFetch(NodeExecutionContext context, String repoPath) throws Exception {
		String remote = context.getParameter("remote", "origin");
		String branch = context.getParameter("branch", "");

		List<String> command = new ArrayList<>(List.of("git", "fetch", remote));
		if (branch != null && !branch.isEmpty()) {
			command.add(branch);
		}
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executeLog(NodeExecutionContext context, String repoPath) throws Exception {
		boolean returnAll = toBoolean(context.getParameter("returnAll", false), false);
		int limit = toInt(context.getParameter("limit", 10), 10);

		List<String> command = new ArrayList<>(List.of("git", "log", "--format=%H%n%an%n%ae%n%aI%n%s%n---END---"));
		if (!returnAll) {
			command.add("-n");
			command.add(String.valueOf(limit));
		}

		Map<String, Object> rawResult = runGitCommand(command, repoPath);
		String stdout = String.valueOf(rawResult.getOrDefault("stdout", ""));

		// Parse log output into structured entries
		List<Map<String, Object>> entries = new ArrayList<>();
		String[] commits = stdout.split("---END---");
		for (String commit : commits) {
			String trimmed = commit.trim();
			if (trimmed.isEmpty()) continue;

			String[] lines = trimmed.split("\n");
			if (lines.length >= 5) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("hash", lines[0].trim());
				entry.put("authorName", lines[1].trim());
				entry.put("authorEmail", lines[2].trim());
				entry.put("date", lines[3].trim());
				entry.put("message", lines[4].trim());
				entries.add(entry);
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("commits", entries);
		result.put("count", entries.size());
		return result;
	}

	private Map<String, Object> executePull(NodeExecutionContext context, String repoPath) throws Exception {
		String remote = context.getParameter("remote", "origin");
		String branch = context.getParameter("branch", "");

		List<String> command = new ArrayList<>(List.of("git", "pull", remote));
		if (branch != null && !branch.isEmpty()) {
			command.add(branch);
		}
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executePush(NodeExecutionContext context, String repoPath) throws Exception {
		String remote = context.getParameter("remote", "origin");
		String branch = context.getParameter("branch", "");

		List<String> command = new ArrayList<>(List.of("git", "push", remote));
		if (branch != null && !branch.isEmpty()) {
			command.add(branch);
		}
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executePushTags(NodeExecutionContext context, String repoPath) throws Exception {
		String remote = context.getParameter("pushTagsRemote", "origin");
		List<String> command = List.of("git", "push", remote, "--tags");
		return runGitCommand(command, repoPath);
	}

	private Map<String, Object> executeStatus(String repoPath) throws Exception {
		List<String> command = List.of("git", "status", "--porcelain");
		Map<String, Object> rawResult = runGitCommand(command, repoPath);
		String stdout = String.valueOf(rawResult.getOrDefault("stdout", ""));

		// Parse status output
		List<Map<String, Object>> files = new ArrayList<>();
		for (String line : stdout.split("\n")) {
			if (line.trim().isEmpty()) continue;
			String status = line.length() >= 2 ? line.substring(0, 2).trim() : "";
			String filePath = line.length() > 3 ? line.substring(3).trim() : line.trim();
			files.add(Map.of("status", status, "path", filePath));
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("files", files);
		result.put("count", files.size());
		result.put("clean", files.isEmpty());
		result.put("raw", stdout);
		return result;
	}

	private Map<String, Object> executeTag(NodeExecutionContext context, String repoPath) throws Exception {
		String name = context.getParameter("name", "");
		List<String> command = List.of("git", "tag", name);
		return runGitCommand(command, repoPath);
	}

	// ========================= Helpers =========================

	private Map<String, Object> runGitCommand(List<String> command, String workingDirectory) throws Exception {
		if (workingDirectory == null || workingDirectory.isBlank()) {
			throw new IllegalArgumentException("Repository path is required.");
		}

		File dir = new File(workingDirectory);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(dir);
		pb.redirectErrorStream(false);

		Process process = pb.start();

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
			throw new RuntimeException("Git command timed out after " + (DEFAULT_TIMEOUT_MS / 1000) + " seconds.");
		}

		stdoutReader.join(5000);
		stderrReader.join(5000);

		int exitCode = process.exitValue();

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("exitCode", exitCode);
		result.put("stdout", stdout.toString());
		result.put("stderr", stderr.toString());
		result.put("success", exitCode == 0);

		if (exitCode != 0) {
			String errorMsg = stderr.toString().isBlank() ? stdout.toString() : stderr.toString();
			if (!errorMsg.isBlank()) {
				log.warn("Git command failed (exit {}): {}", exitCode, errorMsg);
			}
		}

		return result;
	}
}
