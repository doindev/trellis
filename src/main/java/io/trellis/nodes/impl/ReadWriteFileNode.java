package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Read/Write Files from Disk — reads and writes files on the local filesystem.
 */
@Node(
		type = "readWriteFile",
		displayName = "Read/Write Files from Disk",
		description = "Read and write files on the server filesystem",
		category = "File Operations",
		icon = "file"
)
public class ReadWriteFileNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "read");
		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				if ("read".equals(operation)) {
					results.addAll(handleRead(context));
				} else if ("write".equals(operation)) {
					results.add(handleWrite(context));
				}
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private List<Map<String, Object>> handleRead(NodeExecutionContext context) throws IOException {
		String fileSelector = context.getParameter("fileSelector", "");
		String outputField = context.getParameter("outputField", "data");

		List<Map<String, Object>> results = new ArrayList<>();
		Path path = Paths.get(fileSelector);

		List<Path> filesToRead = new ArrayList<>();
		if (Files.exists(path) && !Files.isDirectory(path)) {
			filesToRead.add(path);
		} else if (fileSelector.contains("*") || fileSelector.contains("?")) {
			// Simple glob support
			Path parent = path.getParent();
			if (parent == null) parent = Paths.get(".");
			String pattern = path.getFileName().toString();
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, pattern)) {
				for (Path entry : stream) {
					if (!Files.isDirectory(entry)) {
						filesToRead.add(entry);
					}
				}
			}
		} else {
			throw new IllegalArgumentException("File not found: " + fileSelector);
		}

		for (Path file : filesToRead) {
			byte[] data = Files.readAllBytes(file);
			String fileName = file.getFileName().toString();
			String mimeType = Files.probeContentType(file);
			if (mimeType == null) mimeType = "application/octet-stream";

			Map<String, Object> binary = new LinkedHashMap<>();
			Map<String, Object> fileInfo = new LinkedHashMap<>();
			fileInfo.put("data", Base64.getEncoder().encodeToString(data));
			fileInfo.put("mimeType", mimeType);
			fileInfo.put("fileName", fileName);
			fileInfo.put("fileSize", data.length);
			binary.put(outputField, fileInfo);

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("binary", binary);
			result.put("fileName", fileName);
			result.put("fileSize", data.length);
			result.put("mimeType", mimeType);
			results.add(wrapInJson(result));
		}

		return results;
	}

	private Map<String, Object> handleWrite(NodeExecutionContext context) throws IOException {
		String filePath = context.getParameter("filePath", "");
		String inputField = context.getParameter("inputField", "data");
		boolean append = toBoolean(context.getParameters().get("append"), false);

		@SuppressWarnings("unchecked")
		Map<String, Object> binaryData = (Map<String, Object>)
				context.getInputData().get(0).get("binary");

		byte[] data;
		if (binaryData != null && binaryData.containsKey(inputField)) {
			@SuppressWarnings("unchecked")
			Map<String, Object> fileData = (Map<String, Object>) binaryData.get(inputField);
			data = Base64.getDecoder().decode((String) fileData.get("data"));
		} else {
			throw new IllegalStateException("No binary data found in field: " + inputField);
		}

		Path path = Paths.get(filePath);
		Path parent = path.getParent();
		if (parent != null && !Files.exists(parent)) {
			Files.createDirectories(parent);
		}

		if (append) {
			Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} else {
			Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("fileName", path.getFileName().toString());
		result.put("filePath", path.toAbsolutePath().toString());
		result.put("fileSize", data.length);
		return wrapInJson(result);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("read")
						.options(List.of(
								ParameterOption.builder().name("Read File(s)").value("read").build(),
								ParameterOption.builder().name("Write File").value("write").build()
						)).build(),
				NodeParameter.builder()
						.name("fileSelector").displayName("File(s) Selector")
						.type(ParameterType.STRING).defaultValue("")
						.description("Path to the file or glob pattern (e.g. /data/*.txt).").build(),
				NodeParameter.builder()
						.name("outputField").displayName("Output Binary Field")
						.type(ParameterType.STRING).defaultValue("data")
						.description("Name of the output binary property.").build(),
				NodeParameter.builder()
						.name("filePath").displayName("File Path")
						.type(ParameterType.STRING).defaultValue("")
						.description("Full path including file name to write to.").build(),
				NodeParameter.builder()
						.name("inputField").displayName("Input Binary Field")
						.type(ParameterType.STRING).defaultValue("data")
						.description("Name of the binary property to write.").build(),
				NodeParameter.builder()
						.name("append").displayName("Append")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Append to existing file instead of overwriting.").build()
		);
	}
}
