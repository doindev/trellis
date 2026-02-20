package io.trellis.nodes.impl;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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
 * XML Node - converts between JSON and XML formats.
 */
@Slf4j
@Node(
	type = "xml",
	displayName = "XML",
	description = "Convert between JSON and XML formats.",
	category = "Data Transformation",
	icon = "file-code"
)
public class XmlNode extends AbstractNode {

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
				.name("mode")
				.displayName("Mode")
				.type(ParameterType.OPTIONS)
				.defaultValue("xmlToJson")
				.options(List.of(
					ParameterOption.builder().name("XML to JSON").value("xmlToJson")
						.description("Parse XML string into JSON data").build(),
					ParameterOption.builder().name("JSON to XML").value("jsonToXml")
						.description("Convert JSON data into an XML string").build()
				))
				.build(),

			NodeParameter.builder()
				.name("dataField")
				.displayName("Source Field")
				.description("The field containing the XML string (for XML to JSON) or JSON data (for JSON to XML).")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.required(true)
				.build(),

			NodeParameter.builder()
				.name("destinationKey")
				.displayName("Output Field")
				.description("The field name for the converted output.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.build(),

			NodeParameter.builder()
				.name("options")
				.displayName("Options")
				.type(ParameterType.COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("rootElement")
						.displayName("Root Element Name")
						.description("The root XML element name (for JSON to XML).")
						.type(ParameterType.STRING)
						.defaultValue("root")
						.build(),
					NodeParameter.builder()
						.name("headless")
						.displayName("Headless (No XML Declaration)")
						.description("Omit the XML declaration (<?xml ...?>).")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build(),
					NodeParameter.builder()
						.name("prettyPrint")
						.displayName("Pretty Print")
						.description("Format the XML output with indentation.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(true)
						.build()
				))
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String mode = context.getParameter("mode", "xmlToJson");
		String dataField = context.getParameter("dataField", "data");
		String destinationKey = context.getParameter("destinationKey", "data");

		String rootElement = "root";
		boolean headless = false;
		boolean prettyPrint = true;
		Object optionsObj = context.getParameter("options", null);
		if (optionsObj instanceof Map) {
			Map<String, Object> opts = (Map<String, Object>) optionsObj;
			Object re = opts.get("rootElement");
			if (re != null) rootElement = String.valueOf(re);
			headless = toBoolean(opts.get("headless"), false);
			prettyPrint = toBoolean(opts.get("prettyPrint"), true);
		}

		try {
			List<Map<String, Object>> result = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				Map<String, Object> json = unwrapJson(item);
				Map<String, Object> outputJson = new LinkedHashMap<>(json);

				if ("xmlToJson".equals(mode)) {
					String xml = String.valueOf(json.getOrDefault(dataField, ""));
					Map<String, Object> parsed = parseXmlToMap(xml);
					outputJson.put(destinationKey, parsed);
				} else {
					Object data = json.get(dataField);
					String xml;
					if (data instanceof Map) {
						xml = mapToXml((Map<String, Object>) data, rootElement, headless, prettyPrint);
					} else {
						xml = mapToXml(json, rootElement, headless, prettyPrint);
					}
					outputJson.put(destinationKey, xml);
				}

				result.add(wrapInJson(outputJson));
			}

			log.debug("XML: mode={}, {} items processed", mode, result.size());
			return NodeExecutionResult.success(result);
		} catch (Exception e) {
			return handleError(context, "XML node error: " + e.getMessage(), e);
		}
	}

	/**
	 * Parse an XML string into a Map structure.
	 */
	private Map<String, Object> parseXmlToMap(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// Disable external entities for security
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(new InputSource(new StringReader(xml)));
		doc.getDocumentElement().normalize();

		return elementToMap(doc.getDocumentElement());
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> elementToMap(Element element) {
		Map<String, Object> map = new LinkedHashMap<>();

		// Add attributes
		NamedNodeMap attrs = element.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			map.put("@" + attrs.item(i).getNodeName(), attrs.item(i).getNodeValue());
		}

		// Process child nodes
		NodeList children = element.getChildNodes();
		boolean hasElementChildren = false;

		for (int i = 0; i < children.getLength(); i++) {
			org.w3c.dom.Node child = children.item(i);
			if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
				hasElementChildren = true;
				String childName = child.getNodeName();
				Object childValue = elementToMap((Element) child);

				if (map.containsKey(childName)) {
					// Convert to list if multiple same-name children
					Object existing = map.get(childName);
					if (existing instanceof List) {
						((List<Object>) existing).add(childValue);
					} else {
						List<Object> list = new ArrayList<>();
						list.add(existing);
						list.add(childValue);
						map.put(childName, list);
					}
				} else {
					map.put(childName, childValue);
				}
			}
		}

		// If no element children, get text content
		if (!hasElementChildren) {
			String text = element.getTextContent().trim();
			if (map.isEmpty()) {
				// Simple text element - return as a map with #text
				if (!text.isEmpty()) {
					map.put("#text", text);
				}
			} else if (!text.isEmpty()) {
				map.put("#text", text);
			}
		}

		return map;
	}

	/**
	 * Convert a Map to an XML string.
	 */
	private String mapToXml(Map<String, Object> data, String rootName, boolean headless,
			boolean prettyPrint) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.newDocument();

		Element root = doc.createElement(rootName);
		doc.appendChild(root);
		mapToElement(doc, root, data);

		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		if (prettyPrint) {
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
		}
		if (headless) {
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		}

		StringWriter writer = new StringWriter();
		transformer.transform(new DOMSource(doc), new StreamResult(writer));
		return writer.toString();
	}

	@SuppressWarnings("unchecked")
	private void mapToElement(Document doc, Element parent, Map<String, Object> data) {
		for (Map.Entry<String, Object> entry : data.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			if (key.startsWith("@")) {
				// Attribute
				parent.setAttribute(key.substring(1), String.valueOf(value));
			} else if ("#text".equals(key)) {
				parent.setTextContent(String.valueOf(value));
			} else if (value instanceof Map) {
				Element child = doc.createElement(sanitizeXmlName(key));
				mapToElement(doc, child, (Map<String, Object>) value);
				parent.appendChild(child);
			} else if (value instanceof List) {
				for (Object item : (List<?>) value) {
					Element child = doc.createElement(sanitizeXmlName(key));
					if (item instanceof Map) {
						mapToElement(doc, child, (Map<String, Object>) item);
					} else {
						child.setTextContent(String.valueOf(item));
					}
					parent.appendChild(child);
				}
			} else {
				Element child = doc.createElement(sanitizeXmlName(key));
				child.setTextContent(value != null ? String.valueOf(value) : "");
				parent.appendChild(child);
			}
		}
	}

	private String sanitizeXmlName(String name) {
		// Replace invalid XML chars with underscore
		return name.replaceAll("[^a-zA-Z0-9_.-]", "_")
			.replaceAll("^[^a-zA-Z_]", "_");
	}
}
