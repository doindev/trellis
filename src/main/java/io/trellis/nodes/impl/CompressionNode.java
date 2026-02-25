package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.*;

/**
 * Compression Node — compresses and decompresses files using gzip or zip formats.
 */
@Node(
		type = "compression",
		displayName = "Compression",
		description = "Compress and decompress files (gzip/zip)",
		category = "Data Transformation",
		icon = "file-archive"
)
public class CompressionNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "compress");
		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "compress" -> handleCompress(context);
					case "decompress" -> handleDecompress(context);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleCompress(NodeExecutionContext context) throws Exception {
		String format = context.getParameter("outputFormat", "zip");
		String inputField = context.getParameter("binaryPropertyName", "data");
		String outputField = context.getParameter("outputBinaryPropertyName", "data");

		@SuppressWarnings("unchecked")
		Map<String, Object> binaryData = (Map<String, Object>) context.getInputData().get(0).get("binary");
		if (binaryData == null) {
			throw new IllegalStateException("No binary data found");
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> fileData = (Map<String, Object>) binaryData.get(inputField);
		if (fileData == null) {
			throw new IllegalStateException("No binary data found in field: " + inputField);
		}

		byte[] data = Base64.getDecoder().decode((String) fileData.get("data"));
		String fileName = (String) fileData.getOrDefault("fileName", "file");

		byte[] compressed;
		String mimeType;
		String outputFileName;

		if ("gzip".equals(format)) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
				gos.write(data);
			}
			compressed = bos.toByteArray();
			mimeType = "application/gzip";
			outputFileName = fileName + ".gz";
		} else {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (ZipOutputStream zos = new ZipOutputStream(bos)) {
				ZipEntry entry = new ZipEntry(fileName);
				zos.putNextEntry(entry);
				zos.write(data);
				zos.closeEntry();
			}
			compressed = bos.toByteArray();
			mimeType = "application/zip";
			outputFileName = context.getParameter("fileName", "archive.zip");
		}

		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> binary = new LinkedHashMap<>();
		Map<String, Object> outFile = new LinkedHashMap<>();
		outFile.put("data", Base64.getEncoder().encodeToString(compressed));
		outFile.put("mimeType", mimeType);
		outFile.put("fileName", outputFileName);
		outFile.put("fileSize", compressed.length);
		binary.put(outputField, outFile);
		result.put("binary", binary);

		return result;
	}

	private Map<String, Object> handleDecompress(NodeExecutionContext context) throws Exception {
		String inputField = context.getParameter("binaryPropertyName", "data");
		String outputPrefix = context.getParameter("outputPrefix", "file_");

		@SuppressWarnings("unchecked")
		Map<String, Object> binaryData = (Map<String, Object>) context.getInputData().get(0).get("binary");
		if (binaryData == null) {
			throw new IllegalStateException("No binary data found");
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> fileData = (Map<String, Object>) binaryData.get(inputField);
		if (fileData == null) {
			throw new IllegalStateException("No binary data found in field: " + inputField);
		}

		byte[] data = Base64.getDecoder().decode((String) fileData.get("data"));
		String mimeType = (String) fileData.getOrDefault("mimeType", "");

		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> binary = new LinkedHashMap<>();

		if (mimeType.contains("gzip") || mimeType.contains("gz")) {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			try (GZIPInputStream gis = new GZIPInputStream(bis)) {
				byte[] decompressed = gis.readAllBytes();
				Map<String, Object> outFile = new LinkedHashMap<>();
				outFile.put("data", Base64.getEncoder().encodeToString(decompressed));
				outFile.put("mimeType", "application/octet-stream");
				outFile.put("fileName", outputPrefix + "0");
				outFile.put("fileSize", decompressed.length);
				binary.put(outputPrefix + "0", outFile);
			}
		} else {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			try (ZipInputStream zis = new ZipInputStream(bis)) {
				ZipEntry entry;
				int index = 0;
				while ((entry = zis.getNextEntry()) != null) {
					if (!entry.isDirectory()) {
						byte[] entryData = zis.readAllBytes();
						Map<String, Object> outFile = new LinkedHashMap<>();
						outFile.put("data", Base64.getEncoder().encodeToString(entryData));
						outFile.put("mimeType", "application/octet-stream");
						outFile.put("fileName", entry.getName());
						outFile.put("fileSize", entryData.length);
						binary.put(outputPrefix + index, outFile);
						index++;
					}
				}
			}
		}

		result.put("binary", binary);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("compress")
						.options(List.of(
								ParameterOption.builder().name("Compress").value("compress").build(),
								ParameterOption.builder().name("Decompress").value("decompress").build()
						)).build(),
				NodeParameter.builder()
						.name("binaryPropertyName").displayName("Input Binary Field")
						.type(ParameterType.STRING).defaultValue("data")
						.description("Name of the binary property to process.").build(),
				NodeParameter.builder()
						.name("outputFormat").displayName("Output Format")
						.type(ParameterType.OPTIONS)
						.defaultValue("zip")
						.options(List.of(
								ParameterOption.builder().name("Gzip").value("gzip").build(),
								ParameterOption.builder().name("Zip").value("zip").build()
						)).build(),
				NodeParameter.builder()
						.name("fileName").displayName("File Name")
						.type(ParameterType.STRING).defaultValue("archive.zip")
						.description("Name of the output zip file.").build(),
				NodeParameter.builder()
						.name("outputBinaryPropertyName").displayName("Output Binary Field")
						.type(ParameterType.STRING).defaultValue("data")
						.description("Name of the output binary field.").build(),
				NodeParameter.builder()
						.name("outputPrefix").displayName("Output Prefix")
						.type(ParameterType.STRING).defaultValue("file_")
						.description("Prefix for decompressed file fields.").build()
		);
	}
}
