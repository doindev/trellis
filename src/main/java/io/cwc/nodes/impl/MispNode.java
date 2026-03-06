package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * MISP Node -- manage attributes, events, feeds, galaxies, noticelists,
 * objects, organisations, tags, and warninglists via the MISP REST API.
 */
@Slf4j
@Node(
	type = "misp",
	displayName = "MISP",
	description = "Manage threat intelligence data in MISP",
	category = "Miscellaneous",
	icon = "misp",
	credentials = {"mispApi"},
	searchOnly = true
)
public class MispNode extends AbstractApiNode {

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

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("event")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Attribute").value("attribute").description("Manage attributes").build(),
				ParameterOption.builder().name("Event").value("event").description("Manage events").build(),
				ParameterOption.builder().name("Event Tag").value("eventTag").description("Manage event tags").build(),
				ParameterOption.builder().name("Feed").value("feed").description("Manage feeds").build(),
				ParameterOption.builder().name("Galaxy").value("galaxy").description("Manage galaxies").build(),
				ParameterOption.builder().name("Noticelist").value("noticelist").description("View noticelists").build(),
				ParameterOption.builder().name("Object").value("object").description("View objects").build(),
				ParameterOption.builder().name("Organisation").value("organisation").description("Manage organisations").build(),
				ParameterOption.builder().name("Tag").value("tag").description("Manage tags").build(),
				ParameterOption.builder().name("Warninglist").value("warninglist").description("View warninglists").build()
			)).build());

		addOperationSelectors(params);
		addResourceParameters(params);

		return params;
	}

	private void addOperationSelectors(List<NodeParameter> params) {
		// Attribute operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("attribute"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Event operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Publish").value("publish").build(),
				ParameterOption.builder().name("Unpublish").value("unpublish").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Event Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("eventTag"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").build(),
				ParameterOption.builder().name("Remove").value("remove").build()
			)).build());

		// Feed operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("feed"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Disable").value("disable").build(),
				ParameterOption.builder().name("Enable").value("enable").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build()
			)).build());

		// Galaxy operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("galaxy"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build()
			)).build());

		// Noticelist operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("noticelist"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build()
			)).build());

		// Object operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build()
			)).build());

		// Organisation operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("organisation"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").build(),
				ParameterOption.builder().name("Delete").value("delete").build(),
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build(),
				ParameterOption.builder().name("Update").value("update").build()
			)).build());

		// Warninglist operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("warninglist"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").build(),
				ParameterOption.builder().name("Get Many").value("getAll").build()
			)).build());
	}

	private void addResourceParameters(List<NodeParameter> params) {
		// Attribute parameters
		params.add(NodeParameter.builder()
			.name("attrEventId").displayName("Event ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("attribute"), "operation", List.of("create", "getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("attrType").displayName("Type").type(ParameterType.STRING).required(true)
			.description("Attribute type (ip-src, domain, md5, sha256, url, etc.)")
			.displayOptions(Map.of("show", Map.of("resource", List.of("attribute"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("attrValue").displayName("Value").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("attribute"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("attrCategory").displayName("Category").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("attribute"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("attrId").displayName("Attribute ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("attribute"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("attrUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("attribute"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("value").displayName("Value").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("type").displayName("Type").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("category").displayName("Category").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("comment").displayName("Comment").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("to_ids").displayName("To IDS").type(ParameterType.BOOLEAN).build()
			)).build());

		// Event parameters
		params.add(NodeParameter.builder()
			.name("eventInfo").displayName("Info").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("eventDistribution").displayName("Distribution").type(ParameterType.OPTIONS).defaultValue("0")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Your Organisation Only").value("0").build(),
				ParameterOption.builder().name("This Community Only").value("1").build(),
				ParameterOption.builder().name("Connected Communities").value("2").build(),
				ParameterOption.builder().name("All Communities").value("3").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("eventThreatLevel").displayName("Threat Level").type(ParameterType.OPTIONS).defaultValue("3")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("High").value("1").build(),
				ParameterOption.builder().name("Medium").value("2").build(),
				ParameterOption.builder().name("Low").value("3").build(),
				ParameterOption.builder().name("Undefined").value("4").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("eventAnalysis").displayName("Analysis").type(ParameterType.OPTIONS).defaultValue("0")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Initial").value("0").build(),
				ParameterOption.builder().name("Ongoing").value("1").build(),
				ParameterOption.builder().name("Complete").value("2").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("eventId").displayName("Event ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("get", "delete", "update", "publish", "unpublish"))))
			.build());
		params.add(NodeParameter.builder()
			.name("eventUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("info").displayName("Info").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("distribution").displayName("Distribution").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("threat_level_id").displayName("Threat Level").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("analysis").displayName("Analysis").type(ParameterType.STRING).build()
			)).build());

		// Event Tag parameters
		params.add(NodeParameter.builder()
			.name("etEventId").displayName("Event ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("eventTag"))))
			.build());
		params.add(NodeParameter.builder()
			.name("etTagId").displayName("Tag ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("eventTag"))))
			.build());

		// Feed parameters
		params.add(NodeParameter.builder()
			.name("feedName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("feed"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("feedProvider").displayName("Provider").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("feed"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("feedUrl").displayName("URL").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("feed"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("feedId").displayName("Feed ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("feed"), "operation", List.of("get", "delete", "enable", "disable"))))
			.build());

		// Galaxy parameters
		params.add(NodeParameter.builder()
			.name("galaxyId").displayName("Galaxy ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("galaxy"), "operation", List.of("get", "delete"))))
			.build());

		// Noticelist parameters
		params.add(NodeParameter.builder()
			.name("noticelistId").displayName("Noticelist ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("noticelist"), "operation", List.of("get"))))
			.build());

		// Object parameters
		params.add(NodeParameter.builder()
			.name("objectEventId").displayName("Event ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("objectId").displayName("Object ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("object"), "operation", List.of("get"))))
			.build());

		// Organisation parameters
		params.add(NodeParameter.builder()
			.name("orgName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("organisation"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("orgId").displayName("Organisation ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("organisation"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("orgUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("organisation"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("nationality").displayName("Nationality").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("sector").displayName("Sector").type(ParameterType.STRING).build()
			)).build());

		// Tag parameters
		params.add(NodeParameter.builder()
			.name("tagName").displayName("Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("tagColour").displayName("Colour").type(ParameterType.STRING).defaultValue("#ffffff")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("tagId").displayName("Tag ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("get", "delete", "update"))))
			.build());
		params.add(NodeParameter.builder()
			.name("tagUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("colour").displayName("Colour").type(ParameterType.STRING).build()
			)).build());

		// Warninglist parameters
		params.add(NodeParameter.builder()
			.name("warninglistId").displayName("Warninglist ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("warninglist"), "operation", List.of("get"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "event");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "attribute" -> executeAttribute(context, baseUrl, headers);
				case "event" -> executeEvent(context, baseUrl, headers);
				case "eventTag" -> executeEventTag(context, baseUrl, headers);
				case "feed" -> executeFeed(context, baseUrl, headers);
				case "galaxy" -> executeGalaxy(context, baseUrl, headers);
				case "noticelist" -> executeNoticelist(context, baseUrl, headers);
				case "object" -> executeObject(context, baseUrl, headers);
				case "organisation" -> executeOrganisation(context, baseUrl, headers);
				case "tag" -> executeTag(context, baseUrl, headers);
				case "warninglist" -> executeWarninglist(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "MISP API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeAttribute(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("event_id", context.getParameter("attrEventId", ""));
				body.put("type", context.getParameter("attrType", ""));
				body.put("value", context.getParameter("attrValue", ""));
				String cat = context.getParameter("attrCategory", "");
				if (!cat.isEmpty()) body.put("category", cat);
				HttpResponse<String> response = post(baseUrl + "/attributes/add/" + encode(context.getParameter("attrEventId", "")), body, headers);
				return toResult(response, "Attribute");
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/attributes/view/" + encode(context.getParameter("attrId", "")), headers);
				return toResult(response, "Attribute");
			}
			case "getAll": {
				String eventId = context.getParameter("attrEventId", "");
				HttpResponse<String> response = get(baseUrl + "/attributes/event/" + encode(eventId), headers);
				return toListResult(response, "Attribute");
			}
			case "delete": {
				HttpResponse<String> response = post(baseUrl + "/attributes/delete/" + encode(context.getParameter("attrId", "")), Map.of(), headers);
				return toResult(response, "message");
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("attrUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = post(baseUrl + "/attributes/edit/" + encode(context.getParameter("attrId", "")), body, headers);
				return toResult(response, "Attribute");
			}
			default:
				return NodeExecutionResult.error("Unknown attribute operation: " + operation);
		}
	}

	private NodeExecutionResult executeEvent(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("info", context.getParameter("eventInfo", ""));
				body.put("distribution", context.getParameter("eventDistribution", "0"));
				body.put("threat_level_id", context.getParameter("eventThreatLevel", "3"));
				body.put("analysis", context.getParameter("eventAnalysis", "0"));
				HttpResponse<String> response = post(baseUrl + "/events/add", body, headers);
				return toResult(response, "Event");
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/events/view/" + encode(context.getParameter("eventId", "")), headers);
				return toResult(response, "Event");
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/events/index", headers);
				return toListResult(response, null);
			}
			case "delete": {
				HttpResponse<String> response = post(baseUrl + "/events/delete/" + encode(context.getParameter("eventId", "")), Map.of(), headers);
				return toResult(response, "message");
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("eventUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = post(baseUrl + "/events/edit/" + encode(context.getParameter("eventId", "")), body, headers);
				return toResult(response, "Event");
			}
			case "publish": {
				HttpResponse<String> response = post(baseUrl + "/events/publish/" + encode(context.getParameter("eventId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			case "unpublish": {
				HttpResponse<String> response = post(baseUrl + "/events/unpublish/" + encode(context.getParameter("eventId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			default:
				return NodeExecutionResult.error("Unknown event operation: " + operation);
		}
	}

	private NodeExecutionResult executeEventTag(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "add");
		String eventId = context.getParameter("etEventId", "");
		String tagId = context.getParameter("etTagId", "");

		switch (operation) {
			case "add": {
				HttpResponse<String> response = post(baseUrl + "/events/addTag/" + encode(eventId) + "/" + encode(tagId), Map.of(), headers);
				return toResult(response, null);
			}
			case "remove": {
				HttpResponse<String> response = post(baseUrl + "/events/removeTag/" + encode(eventId) + "/" + encode(tagId), Map.of(), headers);
				return toResult(response, null);
			}
			default:
				return NodeExecutionResult.error("Unknown event tag operation: " + operation);
		}
	}

	private NodeExecutionResult executeFeed(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of(
					"name", context.getParameter("feedName", ""),
					"provider", context.getParameter("feedProvider", ""),
					"url", context.getParameter("feedUrl", ""),
					"input_source", "network"
				);
				HttpResponse<String> response = post(baseUrl + "/feeds/add", Map.of("Feed", body), headers);
				return toResult(response, "Feed");
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/feeds/view/" + encode(context.getParameter("feedId", "")), headers);
				return toResult(response, "Feed");
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/feeds/index", headers);
				return toListResult(response, null);
			}
			case "delete": {
				HttpResponse<String> response = post(baseUrl + "/feeds/delete/" + encode(context.getParameter("feedId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			case "enable": {
				HttpResponse<String> response = post(baseUrl + "/feeds/enable/" + encode(context.getParameter("feedId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			case "disable": {
				HttpResponse<String> response = post(baseUrl + "/feeds/disable/" + encode(context.getParameter("feedId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			default:
				return NodeExecutionResult.error("Unknown feed operation: " + operation);
		}
	}

	private NodeExecutionResult executeGalaxy(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/galaxies/view/" + encode(context.getParameter("galaxyId", "")), headers);
				return toResult(response, "Galaxy");
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/galaxies/index", headers);
				return toListResult(response, null);
			}
			case "delete": {
				HttpResponse<String> response = post(baseUrl + "/galaxies/delete/" + encode(context.getParameter("galaxyId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			default:
				return NodeExecutionResult.error("Unknown galaxy operation: " + operation);
		}
	}

	private NodeExecutionResult executeNoticelist(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/noticelists/view/" + encode(context.getParameter("noticelistId", "")), headers);
				return toResult(response, "Noticelist");
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/noticelists/index", headers);
				return toListResult(response, null);
			}
			default:
				return NodeExecutionResult.error("Unknown noticelist operation: " + operation);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeObject(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/objects/view/" + encode(context.getParameter("objectId", "")), headers);
				return toResult(response, "Object");
			}
			case "getAll": {
				String eventId = context.getParameter("objectEventId", "");
				HttpResponse<String> response = get(baseUrl + "/events/view/" + encode(eventId), headers);
				if (response.statusCode() >= 400) return apiError(response);
				Map<String, Object> parsed = parseResponse(response);
				Object event = parsed.get("Event");
				if (event instanceof Map) {
					Object objects = ((Map<String, Object>) event).get("Object");
					if (objects instanceof List) {
						List<Map<String, Object>> items = new ArrayList<>();
						for (Object o : (List<?>) objects) {
							if (o instanceof Map) items.add(wrapInJson(o));
						}
						return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
					}
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown object operation: " + operation);
		}
	}

	private NodeExecutionResult executeOrganisation(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of("name", context.getParameter("orgName", ""));
				HttpResponse<String> response = post(baseUrl + "/admin/organisations/add", Map.of("Organisation", body), headers);
				return toResult(response, "Organisation");
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/organisations/view/" + encode(context.getParameter("orgId", "")), headers);
				return toResult(response, "Organisation");
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/organisations/index", headers);
				return toListResult(response, null);
			}
			case "delete": {
				HttpResponse<String> response = post(baseUrl + "/admin/organisations/delete/" + encode(context.getParameter("orgId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("orgUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = post(baseUrl + "/admin/organisations/edit/" + encode(context.getParameter("orgId", "")), Map.of("Organisation", body), headers);
				return toResult(response, "Organisation");
			}
			default:
				return NodeExecutionResult.error("Unknown organisation operation: " + operation);
		}
	}

	private NodeExecutionResult executeTag(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of("name", context.getParameter("tagName", ""), "colour", context.getParameter("tagColour", "#ffffff"));
				HttpResponse<String> response = post(baseUrl + "/tags/add", Map.of("Tag", body), headers);
				return toResult(response, "Tag");
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/tags/view/" + encode(context.getParameter("tagId", "")), headers);
				return toResult(response, "Tag");
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/tags/index", headers);
				return toListResult(response, "Tag");
			}
			case "delete": {
				HttpResponse<String> response = post(baseUrl + "/tags/delete/" + encode(context.getParameter("tagId", "")), Map.of(), headers);
				return toResult(response, null);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("tagUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>(updateFields);
				HttpResponse<String> response = post(baseUrl + "/tags/edit/" + encode(context.getParameter("tagId", "")), Map.of("Tag", body), headers);
				return toResult(response, "Tag");
			}
			default:
				return NodeExecutionResult.error("Unknown tag operation: " + operation);
		}
	}

	private NodeExecutionResult executeWarninglist(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		switch (operation) {
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/warninglists/view/" + encode(context.getParameter("warninglistId", "")), headers);
				return toResult(response, "Warninglist");
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/warninglists/index", headers);
				return toListResult(response, "Warninglist");
			}
			default:
				return NodeExecutionResult.error("Unknown warninglist operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("url", ""));
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		headers.put("Authorization", String.valueOf(credentials.getOrDefault("apiKey", "")));
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) return apiError(response);
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		if (dataKey != null && parsed.containsKey(dataKey)) {
			return NodeExecutionResult.success(List.of(wrapInJson(parsed.get(dataKey))));
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) return apiError(response);
		String body = response.body();
		if (body != null && body.trim().startsWith("[")) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Map<String, Object> item : parseArrayResponse(response)) {
				if (dataKey != null && item.containsKey(dataKey)) {
					Object inner = item.get(dataKey);
					if (inner instanceof Map) {
						items.add(wrapInJson(inner));
						continue;
					}
				}
				items.add(wrapInJson(item));
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		Map<String, Object> parsed = parseResponse(response);
		if (dataKey != null && parsed.containsKey(dataKey)) {
			Object data = parsed.get(dataKey);
			if (data instanceof List) {
				List<Map<String, Object>> items = new ArrayList<>();
				for (Object d : (List<?>) data) {
					if (d instanceof Map) items.add(wrapInJson(d));
				}
				return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
			}
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("MISP API error (HTTP " + response.statusCode() + "): " + body);
	}
}
