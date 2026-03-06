package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class XmlNodeTest {

    private XmlNode node;

    @BeforeEach
    void setUp() {
        node = new XmlNode();
    }

    // ── XML to JSON: simple ──

    @Test
    void xmlToJsonSimple() {
        String xml = "<root><name>Alice</name><age>30</age></root>";
        List<Map<String, Object>> input = items(Map.of("data", xml));
        Map<String, Object> params = mutableMap(
                "mode", "xmlToJson",
                "dataField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        Object parsed = firstJson(result).get("data");
        assertThat(parsed).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        // The root element content is parsed into nested maps
        // <name>Alice</name> becomes { "name": { "#text": "Alice" } }
        assertThat(map).containsKey("name");
        assertThat(map).containsKey("age");
    }

    @Test
    void xmlToJsonWithAttributes() {
        String xml = "<item id=\"123\" type=\"widget\"><name>Widget A</name></item>";
        List<Map<String, Object>> input = items(Map.of("data", xml));
        Map<String, Object> params = mutableMap(
                "mode", "xmlToJson",
                "dataField", "data",
                "destinationKey", "parsed"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) firstJson(result).get("parsed");
        assertThat(parsed).containsKey("@id");
        assertThat(parsed.get("@id")).isEqualTo("123");
        assertThat(parsed).containsKey("@type");
        assertThat(parsed.get("@type")).isEqualTo("widget");
    }

    @Test
    void xmlToJsonRepeatedElements() {
        String xml = "<root><item>A</item><item>B</item><item>C</item></root>";
        List<Map<String, Object>> input = items(Map.of("data", xml));
        Map<String, Object> params = mutableMap(
                "mode", "xmlToJson",
                "dataField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) firstJson(result).get("data");
        assertThat(parsed).containsKey("item");
        // Repeated elements become a list
        assertThat(parsed.get("item")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) parsed.get("item");
        assertThat(items).hasSize(3);
    }

    // ── JSON to XML: simple ──

    @Test
    void jsonToXmlSimple() {
        Map<String, Object> jsonData = mutableMap("name", "Alice", "age", "30");
        List<Map<String, Object>> input = items(Map.of("data", jsonData));
        Map<String, Object> params = mutableMap(
                "mode", "jsonToXml",
                "dataField", "data",
                "destinationKey", "data",
                "options", mutableMap("rootElement", "root", "headless", false, "prettyPrint", false)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        String xml = (String) firstJson(result).get("data");
        assertThat(xml).isNotNull();
        assertThat(xml).contains("<root>");
        assertThat(xml).contains("<name>Alice</name>");
        assertThat(xml).contains("<age>30</age>");
        assertThat(xml).contains("</root>");
    }

    // ── JSON to XML: custom root element ──

    @Test
    void jsonToXmlCustomRootElement() {
        Map<String, Object> jsonData = mutableMap("title", "Test");
        List<Map<String, Object>> input = items(Map.of("data", jsonData));
        Map<String, Object> params = mutableMap(
                "mode", "jsonToXml",
                "dataField", "data",
                "destinationKey", "data",
                "options", mutableMap("rootElement", "document", "headless", false, "prettyPrint", false)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String xml = (String) firstJson(result).get("data");
        assertThat(xml).contains("<document>");
        assertThat(xml).contains("</document>");
    }

    // ── JSON to XML: headless (no XML declaration) ──

    @Test
    void jsonToXmlHeadless() {
        Map<String, Object> jsonData = mutableMap("name", "Alice");
        List<Map<String, Object>> input = items(Map.of("data", jsonData));
        Map<String, Object> params = mutableMap(
                "mode", "jsonToXml",
                "dataField", "data",
                "destinationKey", "data",
                "options", mutableMap("rootElement", "root", "headless", true, "prettyPrint", false)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String xml = (String) firstJson(result).get("data");
        assertThat(xml).doesNotContain("<?xml");
    }

    @Test
    void jsonToXmlWithDeclaration() {
        Map<String, Object> jsonData = mutableMap("name", "Alice");
        List<Map<String, Object>> input = items(Map.of("data", jsonData));
        Map<String, Object> params = mutableMap(
                "mode", "jsonToXml",
                "dataField", "data",
                "destinationKey", "data",
                "options", mutableMap("rootElement", "root", "headless", false, "prettyPrint", false)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String xml = (String) firstJson(result).get("data");
        assertThat(xml).contains("<?xml");
    }

    // ── JSON to XML: pretty print ──

    @Test
    void jsonToXmlPrettyPrint() {
        Map<String, Object> jsonData = mutableMap("name", "Alice");
        List<Map<String, Object>> input = items(Map.of("data", jsonData));
        Map<String, Object> params = mutableMap(
                "mode", "jsonToXml",
                "dataField", "data",
                "destinationKey", "data",
                "options", mutableMap("rootElement", "root", "headless", true, "prettyPrint", true)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String xml = (String) firstJson(result).get("data");
        // Pretty print means there should be newlines/indentation
        assertThat(xml).contains("\n");
    }

    @Test
    void jsonToXmlNoPrettyPrint() {
        Map<String, Object> jsonData = mutableMap("name", "Alice");
        List<Map<String, Object>> input = items(Map.of("data", jsonData));
        Map<String, Object> params = mutableMap(
                "mode", "jsonToXml",
                "dataField", "data",
                "destinationKey", "data",
                "options", mutableMap("rootElement", "root", "headless", true, "prettyPrint", false)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String xml = (String) firstJson(result).get("data");
        assertThat(xml).isNotNull();
        // Should contain the element without indentation newlines in the content
        assertThat(xml).contains("<root><name>Alice</name></root>");
    }

    // ── Custom source and destination fields ──

    @Test
    void customSourceAndDestinationFields() {
        String xml = "<item><value>42</value></item>";
        List<Map<String, Object>> input = items(Map.of("xmlContent", xml));
        Map<String, Object> params = mutableMap(
                "mode", "xmlToJson",
                "dataField", "xmlContent",
                "destinationKey", "jsonResult"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("jsonResult");
        assertThat(firstJson(result)).containsKey("xmlContent");
    }

    // ── Empty input ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "mode", "xmlToJson",
                "dataField", "data",
                "destinationKey", "data"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    // ── Multiple items ──

    @Test
    void multipleItemsXmlToJson() {
        List<Map<String, Object>> input = items(
                Map.of("data", "<root><a>1</a></root>"),
                Map.of("data", "<root><b>2</b></root>")
        );
        Map<String, Object> params = mutableMap(
                "mode", "xmlToJson",
                "dataField", "data",
                "destinationKey", "parsed"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsKey("parsed");
        assertThat(jsonAt(result, 1)).containsKey("parsed");
    }

    // ── Roundtrip: JSON -> XML -> JSON preserves structure ──

    @Test
    void roundtripJsonXmlJson() {
        Map<String, Object> jsonData = mutableMap("name", "Alice", "city", "NYC");
        List<Map<String, Object>> input1 = items(Map.of("data", jsonData));
        Map<String, Object> params1 = mutableMap(
                "mode", "jsonToXml",
                "dataField", "data",
                "destinationKey", "xmlOut",
                "options", mutableMap("rootElement", "person", "headless", true, "prettyPrint", false)
        );

        NodeExecutionResult result1 = node.execute(ctx(input1, params1));
        String xmlOutput = (String) firstJson(result1).get("xmlOut");
        assertThat(xmlOutput).isNotNull();

        // Now parse it back
        List<Map<String, Object>> input2 = items(Map.of("data", xmlOutput));
        Map<String, Object> params2 = mutableMap(
                "mode", "xmlToJson",
                "dataField", "data",
                "destinationKey", "jsonOut"
        );

        NodeExecutionResult result2 = node.execute(ctx(input2, params2));

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) firstJson(result2).get("jsonOut");
        assertThat(parsed).isNotNull();
        assertThat(parsed).containsKey("name");
        assertThat(parsed).containsKey("city");
    }
}
