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

/**
 * Venafi TLS Protect Cloud Node -- manage certificates and certificate
 * requests via the Venafi Cloud API.
 */
@Slf4j
@Node(
	type = "venafiTlsProtectCloud",
	displayName = "Venafi TLS Protect Cloud",
	description = "Manage certificates and certificate requests in Venafi TLS Protect Cloud",
	category = "Miscellaneous",
	icon = "venafi",
	credentials = {"venafiTlsProtectCloudApi"}
)
public class VenafiTlsProtectCloudNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.venafi.cloud";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("certificate")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Certificate").value("certificate").description("Manage certificates").build(),
				ParameterOption.builder().name("Certificate Request").value("certificateRequest").description("Manage certificate requests").build()
			)).build());

		// Certificate operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").description("Delete a certificate").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a certificate").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a certificate").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many certificates").build(),
				ParameterOption.builder().name("Renew").value("renew").description("Renew a certificate").build()
			)).build());

		// Certificate Request operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificateRequest"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a certificate request").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a certificate request").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many certificate requests").build()
			)).build());

		addCertificateParameters(params);
		addCertificateRequestParameters(params);

		return params;
	}

	private void addCertificateParameters(List<NodeParameter> params) {
		// Certificate ID for get/delete/download/renew
		params.add(NodeParameter.builder()
			.name("certificateId").displayName("Certificate ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("get", "delete", "download", "renew"))))
			.build());

		// Certificate > Download format
		params.add(NodeParameter.builder()
			.name("downloadFormat").displayName("Format").type(ParameterType.OPTIONS).defaultValue("PEM")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("download"))))
			.options(List.of(
				ParameterOption.builder().name("PEM").value("PEM").build(),
				ParameterOption.builder().name("DER").value("DER").build(),
				ParameterOption.builder().name("JKS").value("JKS").build(),
				ParameterOption.builder().name("PKCS12").value("PKCS12").build()
			)).build());

		// Certificate > GetAll limit
		params.add(NodeParameter.builder()
			.name("certLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("getAll"))))
			.build());
	}

	private void addCertificateRequestParameters(List<NodeParameter> params) {
		// Certificate Request > Create
		params.add(NodeParameter.builder()
			.name("crCommonName").displayName("Common Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificateRequest"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("crIssuingTemplateId").displayName("Issuing Template ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificateRequest"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("crAdditionalFields").displayName("Additional Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificateRequest"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("organizationId").displayName("Organization ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("validityPeriod").displayName("Validity Period").type(ParameterType.STRING)
					.description("e.g. P365D for 365 days").build(),
				NodeParameter.builder().name("subjectAlternativeNames").displayName("Subject Alternative Names").type(ParameterType.STRING)
					.description("Comma-separated SANs").build()
			)).build());

		// Certificate Request > Get
		params.add(NodeParameter.builder()
			.name("crId").displayName("Certificate Request ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificateRequest"), "operation", List.of("get"))))
			.build());

		// Certificate Request > GetAll limit
		params.add(NodeParameter.builder()
			.name("crLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificateRequest"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "certificate");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "certificate" -> executeCertificate(context, headers);
				case "certificateRequest" -> executeCertificateRequest(context, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Venafi TLS Protect Cloud API error: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeCertificate(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "get": {
				String certId = context.getParameter("certificateId", "");
				HttpResponse<String> response = get(BASE_URL + "/outagedetection/v1/certificates/" + encode(certId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("certLimit", 25), 25);
				String url = buildUrl(BASE_URL + "/outagedetection/v1/certificates", Map.of("limit", limit));
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) return apiError(response);
				Map<String, Object> parsed = parseResponse(response);
				Object certs = parsed.get("certificates");
				if (certs instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object c : (List<?>) certs) {
						if (c instanceof Map) items.add(wrapInJson(c));
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			case "delete": {
				String certId = context.getParameter("certificateId", "");
				HttpResponse<String> response = delete(BASE_URL + "/outagedetection/v1/certificates/" + encode(certId), headers);
				return toResult(response);
			}
			case "download": {
				String certId = context.getParameter("certificateId", "");
				String format = context.getParameter("downloadFormat", "PEM");
				Map<String, Object> body = Map.of("certificateIds", List.of(certId), "format", format);
				HttpResponse<String> response = post(BASE_URL + "/outagedetection/v1/certificates/retrieval", body, headers);
				return toResult(response);
			}
			case "renew": {
				String certId = context.getParameter("certificateId", "");
				Map<String, Object> body = Map.of("existingCertificateId", certId);
				HttpResponse<String> response = post(BASE_URL + "/outagedetection/v1/certificaterequests", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown certificate operation: " + operation);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeCertificateRequest(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("commonName", context.getParameter("crCommonName", ""));
				body.put("issuingTemplateId", context.getParameter("crIssuingTemplateId", ""));
				Map<String, Object> additional = context.getParameter("crAdditionalFields", Map.of());
				if (additional.get("organizationId") != null) body.put("organizationId", additional.get("organizationId"));
				if (additional.get("validityPeriod") != null) body.put("validityPeriod", additional.get("validityPeriod"));
				String sans = String.valueOf(additional.getOrDefault("subjectAlternativeNames", ""));
				if (!sans.isEmpty()) {
					Map<String, Object> sanObj = Map.of("dnsNames", Arrays.asList(sans.split(",")));
					body.put("subjectAlternativeNamesByType", sanObj);
				}

				HttpResponse<String> response = post(BASE_URL + "/outagedetection/v1/certificaterequests", body, headers);
				return toResult(response);
			}
			case "get": {
				String crId = context.getParameter("crId", "");
				HttpResponse<String> response = get(BASE_URL + "/outagedetection/v1/certificaterequests/" + encode(crId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("crLimit", 25), 25);
				String url = buildUrl(BASE_URL + "/outagedetection/v1/certificaterequests", Map.of("limit", limit));
				HttpResponse<String> response = get(url, headers);
				if (response.statusCode() >= 400) return apiError(response);
				Map<String, Object> parsed = parseResponse(response);
				Object reqs = parsed.get("certificateRequests");
				if (reqs instanceof List) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Object r : (List<?>) reqs) {
						if (r instanceof Map) items.add(wrapInJson(r));
					}
					return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
			}
			default:
				return NodeExecutionResult.error("Unknown certificate request operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("tppl-api-key", String.valueOf(credentials.getOrDefault("apiKey", "")));
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
		return NodeExecutionResult.error("Venafi Cloud API error (HTTP " + response.statusCode() + "): " + body);
	}
}
