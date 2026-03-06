package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractDocumentLoaderNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Default Data Loader — converts the incoming JSON data items into LangChain4j
 * Document objects that can be fed to vector stores and other AI consumers.
 * Mirrors n8n's documentDefaultDataLoader node.
 *
 * For each input item, extracts text from a specified JSON field (or serialises the
 * whole item) and wraps it in a Document with optional metadata.
 */
@Node(
		type = "documentDefaultDataLoader",
		displayName = "Default Data Loader",
		description = "Load data from previous node output as AI documents",
		category = "AI / Document Loaders",
		icon = "file-import"
)
public class DefaultDataLoaderNode extends AbstractDocumentLoaderNode {

	@SuppressWarnings("unchecked")
	@Override
	public Object supplyData(NodeExecutionContext context) throws Exception {
		String dataField = context.getParameter("dataField", "data");
		String metadataField = context.getParameter("metadataField", "");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Document> documents = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			Object json = item.get("json");
			if (json == null) json = item;

			String text;
			if (json instanceof Map) {
				Map<String, Object> jsonMap = (Map<String, Object>) json;
				Object fieldValue = jsonMap.get(dataField);
				if (fieldValue != null) {
					text = fieldValue.toString();
				} else {
					// Serialize the whole JSON object as text
					text = jsonMap.toString();
				}
			} else {
				text = json.toString();
			}

			if (text.isBlank()) continue;

			Metadata metadata = new Metadata();
			if (!metadataField.isBlank() && json instanceof Map) {
				Object meta = ((Map<String, Object>) json).get(metadataField);
				if (meta instanceof Map) {
					((Map<String, Object>) meta).forEach((k, v) -> {
						if (v != null) metadata.put(k, v.toString());
					});
				}
			}

			documents.add(Document.from(text, metadata));
		}

		return documents;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("dataField").displayName("Data Field")
						.type(ParameterType.STRING)
						.defaultValue("data")
						.description("Name of the JSON field containing the text content. "
								+ "If not found, the entire JSON object is used.").build(),
				NodeParameter.builder()
						.name("metadataField").displayName("Metadata Field")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Optional JSON field containing a metadata object to attach to each document.").build()
		);
	}
}
