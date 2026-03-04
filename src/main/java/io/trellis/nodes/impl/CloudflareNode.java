package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "cloudflare",
	displayName = "Cloudflare",
	description = "Manage Cloudflare zones, DNS records, and SSL certificates.",
	category = "Miscellaneous",
	icon = "cloudflare",
	credentials = {"cloudflareApi"},
	searchOnly = true
)
public class CloudflareNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.cloudflare.com/client/v4";

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

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("zone")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Zone").value("zone").description("Manage zones").build(),
				ParameterOption.builder().name("Zone Certificate").value("zoneCertificate").description("Manage zone SSL certificates").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addZoneParameters(params);
		addZoneCertificateParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Zone operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("zone"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all zones").build()
			)).build());

		// Zone Certificate operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("zoneCertificate"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a certificate").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a certificate").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a certificate").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all certificates").build()
			)).build());
	}

	// ========================= Zone Parameters =========================

	private void addZoneParameters(List<NodeParameter> params) {
		// Zone > GetAll: filters
		params.add(NodeParameter.builder()
			.name("zoneFilters").displayName("Filters")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("zone"), "operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Domain Name").type(ParameterType.STRING)
					.placeHolder("example.com").build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Active").value("active").build(),
						ParameterOption.builder().name("Pending").value("pending").build(),
						ParameterOption.builder().name("Initializing").value("initializing").build(),
						ParameterOption.builder().name("Moved").value("moved").build(),
						ParameterOption.builder().name("Deleted").value("deleted").build(),
						ParameterOption.builder().name("Deactivated").value("deactivated").build()
					)).build(),
				NodeParameter.builder().name("page").displayName("Page").type(ParameterType.NUMBER).defaultValue(1).build(),
				NodeParameter.builder().name("per_page").displayName("Per Page").type(ParameterType.NUMBER).defaultValue(20).build(),
				NodeParameter.builder().name("order").displayName("Order By").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Name").value("name").build(),
						ParameterOption.builder().name("Status").value("status").build(),
						ParameterOption.builder().name("Account Name").value("account.name").build()
					)).build()
			)).build());
	}

	// ========================= Zone Certificate Parameters =========================

	private void addZoneCertificateParameters(List<NodeParameter> params) {
		// Zone Certificate: zone ID (required for all operations)
		params.add(NodeParameter.builder()
			.name("zoneId").displayName("Zone ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("zoneCertificate"))))
			.build());

		// Zone Certificate > Create: hostnames
		params.add(NodeParameter.builder()
			.name("certHostnames").displayName("Hostnames (comma-separated)").type(ParameterType.STRING).required(true)
			.description("Comma-separated list of hostnames for the certificate.")
			.placeHolder("example.com,*.example.com")
			.displayOptions(Map.of("show", Map.of("resource", List.of("zoneCertificate"), "operation", List.of("create"))))
			.build());

		// Zone Certificate > Create: request type
		params.add(NodeParameter.builder()
			.name("certRequestType").displayName("Request Type").type(ParameterType.OPTIONS).required(true).defaultValue("origin-rsa")
			.displayOptions(Map.of("show", Map.of("resource", List.of("zoneCertificate"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Origin RSA").value("origin-rsa").build(),
				ParameterOption.builder().name("Origin ECC").value("origin-ecc").build()
			)).build());

		// Zone Certificate > Create: validity
		params.add(NodeParameter.builder()
			.name("certValidity").displayName("Validity (days)").type(ParameterType.OPTIONS).defaultValue("5475")
			.displayOptions(Map.of("show", Map.of("resource", List.of("zoneCertificate"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("7 days").value("7").build(),
				ParameterOption.builder().name("30 days").value("30").build(),
				ParameterOption.builder().name("90 days").value("90").build(),
				ParameterOption.builder().name("1 year").value("365").build(),
				ParameterOption.builder().name("2 years").value("730").build(),
				ParameterOption.builder().name("3 years").value("1095").build(),
				ParameterOption.builder().name("15 years").value("5475").build()
			)).build());

		// Zone Certificate > Create: CSR
		params.add(NodeParameter.builder()
			.name("certCsr").displayName("CSR").type(ParameterType.STRING)
			.description("Certificate Signing Request. If empty, Cloudflare will generate one.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("zoneCertificate"), "operation", List.of("create"))))
			.build());

		// Zone Certificate > Get/Delete: certificate ID
		params.add(NodeParameter.builder()
			.name("certificateId").displayName("Certificate ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("zoneCertificate"), "operation", List.of("get", "delete"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "zone");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "zone" -> executeZone(context, headers);
				case "zoneCertificate" -> executeZoneCertificate(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Cloudflare API error: " + e.getMessage(), e);
		}
	}

	// ========================= Zone Execute =========================

	private NodeExecutionResult executeZone(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		Map<String, Object> filters = context.getParameter("zoneFilters", Map.of());

		Map<String, Object> queryParams = new LinkedHashMap<>();
		if (filters.get("name") != null) queryParams.put("name", filters.get("name"));
		if (filters.get("status") != null) queryParams.put("status", filters.get("status"));
		if (filters.get("page") != null) queryParams.put("page", filters.get("page"));
		if (filters.get("per_page") != null) queryParams.put("per_page", filters.get("per_page"));
		if (filters.get("order") != null) queryParams.put("order", filters.get("order"));

		String url = buildUrl(BASE_URL + "/zones", queryParams);
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError(response);
		}

		Map<String, Object> parsed = parseResponse(response);
		Object resultData = parsed.get("result");
		if (resultData instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) resultData) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	// ========================= Zone Certificate Execute =========================

	private NodeExecutionResult executeZoneCertificate(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");
		String zoneId = context.getParameter("zoneId", "");
		String baseUrl = BASE_URL + "/zones/" + encode(zoneId) + "/ssl/certificate_packs";

		switch (operation) {
			case "create": {
				String hostnames = context.getParameter("certHostnames", "");
				String requestType = context.getParameter("certRequestType", "origin-rsa");
				String validity = context.getParameter("certValidity", "5475");
				String csr = context.getParameter("certCsr", "");

				// Use origin certificates endpoint
				String url = BASE_URL + "/certificates";
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("hostnames", Arrays.asList(hostnames.split(",")));
				body.put("request_type", requestType);
				body.put("requested_validity", Integer.parseInt(validity));
				if (!csr.isEmpty()) {
					body.put("csr", csr);
				}

				HttpResponse<String> response = post(url, body, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object result = parsed.getOrDefault("result", parsed);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete": {
				String certificateId = context.getParameter("certificateId", "");
				String url = BASE_URL + "/certificates/" + encode(certificateId);
				HttpResponse<String> response = delete(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				if (response.body() == null || response.body().isBlank()) {
					return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
				}
				Map<String, Object> parsed = parseResponse(response);
				return NodeExecutionResult.success(List.of(wrapInJson(parsed.getOrDefault("result", parsed))));
			}
			case "get": {
				String certificateId = context.getParameter("certificateId", "");
				String url = BASE_URL + "/certificates/" + encode(certificateId);
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object result = parsed.getOrDefault("result", parsed);
				return NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "getAll": {
				String url = BASE_URL + "/certificates?zone_id=" + encode(zoneId);
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				Map<String, Object> parsed = parseResponse(response);
				Object resultData = parsed.get("result");
				if (resultData instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object item : (List<?>) resultData) {
						if (item instanceof Map) {
							items.add(wrapInJson(item));
						}
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown zone certificate operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");

		String authType = String.valueOf(credentials.getOrDefault("authType", "bearerToken"));

		if ("apiKey".equals(authType)) {
			String email = String.valueOf(credentials.getOrDefault("email", ""));
			String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
			headers.put("X-Auth-Email", email);
			headers.put("X-Auth-Key", apiKey);
		} else {
			String token = String.valueOf(credentials.getOrDefault("apiToken", credentials.getOrDefault("accessToken", "")));
			headers.put("Authorization", "Bearer " + token);
		}

		return headers;
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Cloudflare API error (HTTP " + response.statusCode() + "): " + body);
	}
}
