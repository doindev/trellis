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
 * Netscaler ADC Node -- manage certificates, files, servers, and service
 * groups via the Netscaler ADC (Citrix ADC) NITRO API v1.
 */
@Slf4j
@Node(
	type = "netscalerAdc",
	displayName = "Netscaler ADC",
	description = "Manage certificates, files, servers, and service groups in Netscaler ADC",
	category = "Miscellaneous",
	icon = "netscaler",
	credentials = {"netscalerAdcApi"},
	searchOnly = true
)
public class NetscalerAdcNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("server")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Certificate").value("certificate").description("Manage SSL certificates").build(),
				ParameterOption.builder().name("File").value("file").description("Manage files").build(),
				ParameterOption.builder().name("Server").value("server").description("Manage servers").build(),
				ParameterOption.builder().name("Service Group").value("serviceGroup").description("Manage service groups").build()
			)).build());

		// Certificate operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an SSL certificate").build(),
				ParameterOption.builder().name("Install").value("install").description("Install an SSL certificate").build()
			)).build());

		// File operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("upload")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").description("Delete a file").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a file").build(),
				ParameterOption.builder().name("Upload").value("upload").description("Upload a file").build()
			)).build());

		// Server operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("server"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a server").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a server").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a server").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all servers").build(),
				ParameterOption.builder().name("Rename").value("rename").description("Rename a server").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a server").build()
			)).build());

		// Service Group operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("serviceGroup"))))
			.options(List.of(
				ParameterOption.builder().name("Add Member").value("add").description("Add a member to a service group").build(),
				ParameterOption.builder().name("Bind").value("bind").description("Bind a service group to a virtual server").build(),
				ParameterOption.builder().name("Create").value("create").description("Create a service group").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a service group").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a service group").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all service groups").build()
			)).build());

		addResourceParameters(params);

		return params;
	}

	private void addResourceParameters(List<NodeParameter> params) {
		// Certificate parameters
		params.add(NodeParameter.builder()
			.name("certName").displayName("Certificate Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"))))
			.build());
		params.add(NodeParameter.builder()
			.name("certFile").displayName("Certificate File").type(ParameterType.STRING).required(true)
			.description("Path to the certificate file on the ADC")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("install"))))
			.build());
		params.add(NodeParameter.builder()
			.name("certKeyFile").displayName("Key File").type(ParameterType.STRING)
			.description("Path to the key file on the ADC")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("install"))))
			.build());
		params.add(NodeParameter.builder()
			.name("certReqFile").displayName("CSR File").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("create"))))
			.build());

		// File parameters
		params.add(NodeParameter.builder()
			.name("fileName").displayName("File Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.build());
		params.add(NodeParameter.builder()
			.name("fileLocation").displayName("File Location").type(ParameterType.OPTIONS).defaultValue("/nsconfig/ssl/")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
			.options(List.of(
				ParameterOption.builder().name("/nsconfig/ssl/").value("/nsconfig/ssl/").build(),
				ParameterOption.builder().name("/var/tmp/").value("/var/tmp/").build(),
				ParameterOption.builder().name("/var/nslog/").value("/var/nslog/").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("fileContent").displayName("File Content").type(ParameterType.STRING).required(true)
			.description("Base64-encoded file content")
			.displayOptions(Map.of("show", Map.of("resource", List.of("file"), "operation", List.of("upload"))))
			.build());

		// Server parameters
		params.add(NodeParameter.builder()
			.name("serverName").displayName("Server Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("server"), "operation", List.of("create", "get", "delete", "update", "rename"))))
			.build());
		params.add(NodeParameter.builder()
			.name("serverIpAddress").displayName("IP Address").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("server"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("serverNewName").displayName("New Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("server"), "operation", List.of("rename"))))
			.build());
		params.add(NodeParameter.builder()
			.name("serverUpdateFields").displayName("Update Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("server"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("comment").displayName("Comment").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("ipaddress").displayName("IP Address").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("state").displayName("State").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Enabled").value("ENABLED").build(),
						ParameterOption.builder().name("Disabled").value("DISABLED").build()
					)).build()
			)).build());

		// Service Group parameters
		params.add(NodeParameter.builder()
			.name("sgName").displayName("Service Group Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("serviceGroup"), "operation", List.of("create", "get", "delete", "add", "bind"))))
			.build());
		params.add(NodeParameter.builder()
			.name("sgServiceType").displayName("Service Type").type(ParameterType.OPTIONS).defaultValue("HTTP")
			.displayOptions(Map.of("show", Map.of("resource", List.of("serviceGroup"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("HTTP").value("HTTP").build(),
				ParameterOption.builder().name("HTTPS / SSL").value("SSL").build(),
				ParameterOption.builder().name("TCP").value("TCP").build(),
				ParameterOption.builder().name("UDP").value("UDP").build(),
				ParameterOption.builder().name("SSL_BRIDGE").value("SSL_BRIDGE").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("sgMemberName").displayName("Member Server Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("serviceGroup"), "operation", List.of("add"))))
			.build());
		params.add(NodeParameter.builder()
			.name("sgMemberPort").displayName("Member Port").type(ParameterType.NUMBER).required(true).defaultValue(80)
			.displayOptions(Map.of("show", Map.of("resource", List.of("serviceGroup"), "operation", List.of("add"))))
			.build());
		params.add(NodeParameter.builder()
			.name("sgBindVserver").displayName("Virtual Server Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("serviceGroup"), "operation", List.of("bind"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "server");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "certificate" -> executeCertificate(context, baseUrl, headers);
				case "file" -> executeFile(context, baseUrl, headers);
				case "server" -> executeServer(context, baseUrl, headers);
				case "serviceGroup" -> executeServiceGroup(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Netscaler ADC API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeCertificate(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "create");
		String certName = context.getParameter("certName", "");

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("certkey", certName);
				String csrFile = context.getParameter("certReqFile", "");
				if (!csrFile.isEmpty()) body.put("reqfile", csrFile);
				HttpResponse<String> response = post(baseUrl + "/config/sslcertreq", Map.of("sslcertreq", body), headers);
				return toResult(response);
			}
			case "install": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("certkey", certName);
				body.put("cert", context.getParameter("certFile", ""));
				String keyFile = context.getParameter("certKeyFile", "");
				if (!keyFile.isEmpty()) body.put("key", keyFile);
				HttpResponse<String> response = post(baseUrl + "/config/sslcertkey", Map.of("sslcertkey", body), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown certificate operation: " + operation);
		}
	}

	private NodeExecutionResult executeFile(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "upload");
		String fileName = context.getParameter("fileName", "");
		String fileLocation = context.getParameter("fileLocation", "/nsconfig/ssl/");

		switch (operation) {
			case "upload": {
				String fileContent = context.getParameter("fileContent", "");
				Map<String, Object> body = Map.of("filename", fileName, "filelocation", fileLocation,
					"filecontent", fileContent, "fileencoding", "BASE64");
				HttpResponse<String> response = post(baseUrl + "/config/systemfile", Map.of("systemfile", body), headers);
				return toResult(response);
			}
			case "download": {
				String url = buildUrl(baseUrl + "/config/systemfile", Map.of("args", "filename:" + fileName + ",filelocation:" + fileLocation));
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "delete": {
				String url = buildUrl(baseUrl + "/config/systemfile/" + encode(fileName), Map.of("args", "filelocation:" + fileLocation));
				HttpResponse<String> response = delete(url, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown file operation: " + operation);
		}
	}

	private NodeExecutionResult executeServer(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String serverName = context.getParameter("serverName", "");

		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of("name", serverName, "ipaddress", context.getParameter("serverIpAddress", ""));
				HttpResponse<String> response = post(baseUrl + "/config/server", Map.of("server", body), headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/config/server/" + encode(serverName), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/config/server", headers);
				if (response.statusCode() >= 400) return apiError(response);
				Map<String, Object> parsed = parseResponse(response);
				Object servers = parsed.get("server");
				if (servers instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object s : (List<?>) servers) {
						if (s instanceof Map) items.add(wrapInJson(s));
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/config/server/" + encode(serverName), headers);
				return toResult(response);
			}
			case "rename": {
				String newName = context.getParameter("serverNewName", "");
				Map<String, Object> body = Map.of("name", serverName, "newname", newName);
				HttpResponse<String> response = post(baseUrl + "/config/server?action=rename", Map.of("server", body), headers);
				return toResult(response);
			}
			case "update": {
				Map<String, Object> updateFields = context.getParameter("serverUpdateFields", Map.of());
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", serverName);
				body.putAll(updateFields);
				HttpResponse<String> response = put(baseUrl + "/config/server", Map.of("server", body), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown server operation: " + operation);
		}
	}

	private NodeExecutionResult executeServiceGroup(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String sgName = context.getParameter("sgName", "");

		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of("servicegroupname", sgName,
					"servicetype", context.getParameter("sgServiceType", "HTTP"));
				HttpResponse<String> response = post(baseUrl + "/config/servicegroup", Map.of("servicegroup", body), headers);
				return toResult(response);
			}
			case "get": {
				HttpResponse<String> response = get(baseUrl + "/config/servicegroup/" + encode(sgName), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/config/servicegroup", headers);
				if (response.statusCode() >= 400) return apiError(response);
				Map<String, Object> parsed = parseResponse(response);
				Object groups = parsed.get("servicegroup");
				if (groups instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object g : (List<?>) groups) {
						if (g instanceof Map) items.add(wrapInJson(g));
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "delete": {
				HttpResponse<String> response = delete(baseUrl + "/config/servicegroup/" + encode(sgName), headers);
				return toResult(response);
			}
			case "add": {
				String memberName = context.getParameter("sgMemberName", "");
				int memberPort = toInt(context.getParameter("sgMemberPort", 80), 80);
				Map<String, Object> body = Map.of("servicegroupname", sgName, "servername", memberName, "port", memberPort);
				HttpResponse<String> response = put(baseUrl + "/config/servicegroup_servicegroupmember_binding", Map.of("servicegroup_servicegroupmember_binding", body), headers);
				return toResult(response);
			}
			case "bind": {
				String vserver = context.getParameter("sgBindVserver", "");
				Map<String, Object> body = Map.of("name", vserver, "servicegroupname", sgName);
				HttpResponse<String> response = put(baseUrl + "/config/lbvserver_servicegroup_binding", Map.of("lbvserver_servicegroup_binding", body), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown service group operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("url", ""));
		if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		return url + "/nitro/v1";
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		String username = String.valueOf(credentials.getOrDefault("username", ""));
		String password = String.valueOf(credentials.getOrDefault("password", ""));
		String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
		headers.put("Authorization", "Basic " + auth);
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) return apiError(response);
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Netscaler ADC API error (HTTP " + response.statusCode() + "): " + body);
	}
}
