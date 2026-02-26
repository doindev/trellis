package io.trellis.nodes.impl.ai;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractDocumentLoaderNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON Input Loader — loads JSON data from input items as LangChain4j Documents.
 * Supports a JSON pointer to extract specific text content from nested structures.
 */
@Node(
		type = "documentJsonInputLoader",
		displayName = "JSON Input Loader",
		description = "Load JSON data from input items as AI documents",
		category = "AI / Document Loaders",
		icon = "file-code"
)
public class JsonInputLoaderNode extends AbstractDocumentLoaderNode {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@SuppressWarnings("unchecked")
	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		String jsonDataParam = context.getParameter("jsonData", "");
		String pointerField = context.getParameter("pointerField", "");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Document> documents = new ArrayList<>();

		// If jsonData parameter is provided, parse it directly
		if (jsonDataParam != null && !jsonDataParam.isBlank()) {
			String text = extractTextFromJson(jsonDataParam, pointerField);
			if (text != null && !text.isBlank()) {
				Metadata metadata = new Metadata();
				metadata.put("source", "jsonInput");
				documents.add(Document.from(text, metadata));
			}
			return documents;
		}

		// Otherwise, process each input item
		if (inputData != null) {
			for (Map<String, Object> item : inputData) {
				Object json = item.get("json");
				if (json == null) json = item;

				String jsonString;
				if (json instanceof String) {
					jsonString = (String) json;
				} else {
					jsonString = objectMapper.writeValueAsString(json);
				}

				String text = extractTextFromJson(jsonString, pointerField);
				if (text != null && !text.isBlank()) {
					Metadata metadata = new Metadata();
					metadata.put("source", "jsonInput");
					documents.add(Document.from(text, metadata));
				}
			}
		}

		return documents;
	}

	private String extractTextFromJson(String jsonString, String pointerField) {
		try {
			if (pointerField != null && !pointerField.isBlank()) {
				JsonNode root = objectMapper.readTree(jsonString);
				// Ensure the pointer starts with /
				String pointer = pointerField.startsWith("/") ? pointerField : "/" + pointerField;
				JsonNode target = root.at(JsonPointer.compile(pointer));
				if (!target.isMissingNode()) {
					return target.isTextual() ? target.asText() : target.toString();
				}
				return null;
			}
			return jsonString;
		} catch (Exception e) {
			// If parsing fails, return the raw string
			return jsonString;
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("jsonData").displayName("JSON Data")
						.type(ParameterType.JSON)
						.defaultValue("")
						.description("The JSON data to load. If empty, data is taken from the input items.").build(),
				NodeParameter.builder()
						.name("pointerField").displayName("JSON Pointer")
						.type(ParameterType.STRING)
						.defaultValue("")
						.placeHolder("/data/text")
						.description("Optional JSON Pointer (RFC 6901) to extract specific text from the JSON. "
								+ "For example, '/content' or '/data/text'. If empty, the entire JSON is used.").build()
		);
	}
}
