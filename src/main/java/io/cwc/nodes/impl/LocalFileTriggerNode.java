package io.cwc.nodes.impl;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Local File Trigger Node -- polls a local directory for file changes.
 * Detects new, modified, and deleted files by comparing directory state
 * between polls using staticData persistence.
 */
@Slf4j
@Node(
	type = "localFileTrigger",
	displayName = "Local File Trigger",
	description = "Watches a local directory for file changes (created, modified, deleted).",
	category = "Core Triggers",
	icon = "localFileTrigger",
	trigger = true,
	polling = true
)
public class LocalFileTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("path").displayName("Folder Path")
			.type(ParameterType.STRING).required(true)
			.placeHolder("/home/user/documents")
			.description("The path to the folder to watch for file changes.")
			.build());

		params.add(NodeParameter.builder()
			.name("events").displayName("Events")
			.type(ParameterType.OPTIONS).required(true).defaultValue("all")
			.options(List.of(
				ParameterOption.builder().name("All Events").value("all").description("Detect all file changes").build(),
				ParameterOption.builder().name("File Created").value("created").description("Detect new files only").build(),
				ParameterOption.builder().name("File Changed").value("changed").description("Detect modified files only").build(),
				ParameterOption.builder().name("File Deleted").value("deleted").description("Detect deleted files only").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("recursive").displayName("Recursive")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether to watch subdirectories recursively.")
			.build());

		params.add(NodeParameter.builder()
			.name("fileFilter").displayName("File Filter (glob)")
			.type(ParameterType.STRING).defaultValue("")
			.description("Optional glob pattern to filter files (e.g., *.txt, *.csv). Leave empty to include all files.")
			.placeHolder("*.txt")
			.build());

		params.add(NodeParameter.builder()
			.name("ignoreHiddenFiles").displayName("Ignore Hidden Files")
			.type(ParameterType.BOOLEAN).defaultValue(true)
			.description("Whether to ignore hidden files (files starting with a dot).")
			.build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String folderPath = context.getParameter("path", "");
		String events = context.getParameter("events", "all");
		boolean recursive = toBoolean(context.getParameter("recursive", false), false);
		String fileFilter = context.getParameter("fileFilter", "");
		boolean ignoreHidden = toBoolean(context.getParameter("ignoreHiddenFiles", true), true);

		if (folderPath.isBlank()) {
			return NodeExecutionResult.error("Folder path is required.");
		}

		Path folder = Path.of(folderPath);
		if (!Files.exists(folder) || !Files.isDirectory(folder)) {
			return NodeExecutionResult.error("Path does not exist or is not a directory: " + folderPath);
		}

		try {
			// Get current file state
			Map<String, FileInfo> currentFiles = scanDirectory(folder, recursive, fileFilter, ignoreHidden);

			// Get previous file state from staticData
			Map<String, Object> staticData = context.getStaticData();
			if (staticData == null) {
				staticData = new HashMap<>();
			}

			Map<String, Map<String, Object>> previousState = (Map<String, Map<String, Object>>) staticData.get("fileState");
			boolean isFirstRun = (previousState == null);

			if (isFirstRun) {
				previousState = new HashMap<>();
			}

			// Compare states to find changes
			List<Map<String, Object>> results = new ArrayList<>();

			if (!isFirstRun) {
				// Detect created and changed files
				if ("all".equals(events) || "created".equals(events) || "changed".equals(events)) {
					for (Map.Entry<String, FileInfo> entry : currentFiles.entrySet()) {
						String path = entry.getKey();
						FileInfo info = entry.getValue();
						Map<String, Object> prev = previousState.get(path);

						if (prev == null) {
							// New file
							if ("all".equals(events) || "created".equals(events)) {
								Map<String, Object> item = new LinkedHashMap<>();
								item.put("event", "created");
								item.put("path", path);
								item.put("fileName", info.fileName);
								item.put("size", info.size);
								item.put("lastModified", info.lastModified);
								item.put("isDirectory", info.isDirectory);
								results.add(createTriggerItem(item));
							}
						} else {
							// Check if modified
							long prevModified = ((Number) prev.getOrDefault("lastModified", 0L)).longValue();
							long prevSize = ((Number) prev.getOrDefault("size", 0L)).longValue();
							if (info.lastModified != prevModified || info.size != prevSize) {
								if ("all".equals(events) || "changed".equals(events)) {
									Map<String, Object> item = new LinkedHashMap<>();
									item.put("event", "changed");
									item.put("path", path);
									item.put("fileName", info.fileName);
									item.put("size", info.size);
									item.put("previousSize", prevSize);
									item.put("lastModified", info.lastModified);
									item.put("previousLastModified", prevModified);
									item.put("isDirectory", info.isDirectory);
									results.add(createTriggerItem(item));
								}
							}
						}
					}
				}

				// Detect deleted files
				if ("all".equals(events) || "deleted".equals(events)) {
					for (Map.Entry<String, Map<String, Object>> entry : previousState.entrySet()) {
						String path = entry.getKey();
						if (!currentFiles.containsKey(path)) {
							Map<String, Object> item = new LinkedHashMap<>();
							item.put("event", "deleted");
							item.put("path", path);
							item.put("fileName", entry.getValue().getOrDefault("fileName", ""));
							item.put("previousSize", entry.getValue().getOrDefault("size", 0));
							item.put("previousLastModified", entry.getValue().getOrDefault("lastModified", 0));
							results.add(createTriggerItem(item));
						}
					}
				}
			}

			// Save current state for next poll
			Map<String, Map<String, Object>> newState = new HashMap<>();
			for (Map.Entry<String, FileInfo> entry : currentFiles.entrySet()) {
				Map<String, Object> fileData = new LinkedHashMap<>();
				fileData.put("fileName", entry.getValue().fileName);
				fileData.put("size", entry.getValue().size);
				fileData.put("lastModified", entry.getValue().lastModified);
				fileData.put("isDirectory", entry.getValue().isDirectory);
				newState.put(entry.getKey(), fileData);
			}

			Map<String, Object> updatedStaticData = new HashMap<>(staticData);
			updatedStaticData.put("fileState", newState);

			if (results.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(updatedStaticData)
					.build();
			}

			return NodeExecutionResult.builder()
				.output(List.of(results))
				.staticData(updatedStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "Local File Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Directory Scanning =========================

	private Map<String, FileInfo> scanDirectory(Path folder, boolean recursive, String fileFilter, boolean ignoreHidden) throws IOException {
		Map<String, FileInfo> files = new LinkedHashMap<>();

		int maxDepth = recursive ? Integer.MAX_VALUE : 1;

		try (Stream<Path> stream = Files.walk(folder, maxDepth)) {
			stream.forEach(path -> {
				try {
					// Skip the root folder itself
					if (path.equals(folder)) {
						return;
					}

					String fileName = path.getFileName().toString();

					// Skip hidden files if configured
					if (ignoreHidden && fileName.startsWith(".")) {
						return;
					}

					// Apply glob filter
					if (!fileFilter.isEmpty() && !Files.isDirectory(path)) {
						PathMatcher matcher = path.getFileSystem().getPathMatcher("glob:" + fileFilter);
						if (!matcher.matches(path.getFileName())) {
							return;
						}
					}

					BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
					FileInfo info = new FileInfo();
					info.fileName = fileName;
					info.size = attrs.size();
					info.lastModified = attrs.lastModifiedTime().toMillis();
					info.isDirectory = attrs.isDirectory();

					files.put(path.toAbsolutePath().toString(), info);
				} catch (IOException e) {
					log.warn("Could not read file attributes for: {}", path, e);
				}
			});
		}

		return files;
	}

	private static class FileInfo {
		String fileName;
		long size;
		long lastModified;
		boolean isDirectory;
	}
}
