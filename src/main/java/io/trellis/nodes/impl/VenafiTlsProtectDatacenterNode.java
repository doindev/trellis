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
 * Venafi TLS Protect Datacenter Node -- manage certificates and policies
 * via the Venafi TPP REST API (self-hosted).
 */
@Slf4j
@Node(
	type = "venafiTlsProtectDatacenter",
	displayName = "Venafi TLS Protect Datacenter",
	description = "Manage certificates and policies in Venafi TLS Protect Datacenter",
	category = "Miscellaneous",
	icon = "venafi",
	credentials = {"venafiTlsProtectDatacenterApi"},
	searchOnly = true
)
public class VenafiTlsProtectDatacenterNode extends AbstractApiNode {

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
				ParameterOption.builder().name("Policy").value("policy").description("View policies").build()
			)).build());

		// Certificate operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Request a new certificate").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a certificate").build(),
				ParameterOption.builder().name("Download").value("download").description("Download a certificate").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a certificate").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many certificates").build(),
				ParameterOption.builder().name("Renew").value("renew").description("Renew a certificate").build()
			)).build());

		// Policy operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("policy"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get policy details").build()
			)).build());

		addCertificateParameters(params);
		addPolicyParameters(params);

		return params;
	}

	private void addCertificateParameters(List<NodeParameter> params) {
		// Certificate > Create
		params.add(NodeParameter.builder()
			.name("certPolicyDN").displayName("Policy DN").type(ParameterType.STRING).required(true)
			.description("Distinguished name of the policy folder, e.g. \\VED\\Policy\\MyApp")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("certSubject").displayName("Subject").type(ParameterType.STRING).required(true)
			.description("Certificate subject, e.g. CN=example.com")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("create"))))
			.build());
		params.add(NodeParameter.builder()
			.name("certAdditionalFields").displayName("Additional Fields").type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("contacts").displayName("Contacts").type(ParameterType.STRING)
					.description("Comma-separated contact emails").build(),
				NodeParameter.builder().name("description").displayName("Description").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("subjectAltNames").displayName("Subject Alt Names").type(ParameterType.STRING)
					.description("Comma-separated SANs").build()
			)).build());

		// Certificate DN for get/delete/download/renew
		params.add(NodeParameter.builder()
			.name("certDN").displayName("Certificate DN").type(ParameterType.STRING).required(true)
			.description("Distinguished name of the certificate")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("get", "delete", "download", "renew"))))
			.build());

		// Certificate > Download format
		params.add(NodeParameter.builder()
			.name("downloadFormat").displayName("Format").type(ParameterType.OPTIONS).defaultValue("Base64")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("download"))))
			.options(List.of(
				ParameterOption.builder().name("Base64").value("Base64").build(),
				ParameterOption.builder().name("DER").value("DER").build(),
				ParameterOption.builder().name("PKCS #7").value("PKCS7").build(),
				ParameterOption.builder().name("PKCS #12").value("PKCS12").build()
			)).build());

		// Certificate > GetAll
		params.add(NodeParameter.builder()
			.name("certSearchFolder").displayName("Policy Folder DN").type(ParameterType.STRING)
			.description("Policy folder to search in")
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("getAll"))))
			.build());
		params.add(NodeParameter.builder()
			.name("certLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(25)
			.displayOptions(Map.of("show", Map.of("resource", List.of("certificate"), "operation", List.of("getAll"))))
			.build());
	}

	private void addPolicyParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("policyDN").displayName("Policy DN").type(ParameterType.STRING).required(true)
			.description("Distinguished name of the policy folder")
			.displayOptions(Map.of("show", Map.of("resource", List.of("policy"), "operation", List.of("get"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "certificate");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials, baseUrl);

			return switch (resource) {
				case "certificate" -> executeCertificate(context, baseUrl, headers);
				case "policy" -> executePolicy(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Venafi Datacenter API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeCertificate(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("PolicyDN", context.getParameter("certPolicyDN", ""));
				body.put("Subject", context.getParameter("certSubject", ""));
				Map<String, Object> additional = context.getParameter("certAdditionalFields", Map.of());
				String contacts = String.valueOf(additional.getOrDefault("contacts", ""));
				if (!contacts.isEmpty()) body.put("Contacts", Arrays.asList(contacts.split(",")));
				putIfPresent(body, "Description", additional.get("description"));
				String sans = String.valueOf(additional.getOrDefault("subjectAltNames", ""));
				if (!sans.isEmpty()) {
					body.put("SubjectAltNames", List.of(Map.of("Type", 2, "Name", sans)));
				}

				HttpResponse<String> response = post(baseUrl + "/vedsdk/certificates/request", body, headers);
				return toResult(response);
			}
			case "get": {
				String certDN = context.getParameter("certDN", "");
				Map<String, Object> qp = Map.of("CertificateDN", certDN);
				HttpResponse<String> response = get(buildUrl(baseUrl + "/vedsdk/certificates", qp), headers);
				return toResult(response);
			}
			case "getAll": {
				Map<String, Object> qp = new LinkedHashMap<>();
				String folder = context.getParameter("certSearchFolder", "");
				if (!folder.isEmpty()) qp.put("ParentDnRecursive", folder);
				qp.put("Limit", toInt(context.getParameter("certLimit", 25), 25));
				HttpResponse<String> response = get(buildUrl(baseUrl + "/vedsdk/certificates", qp), headers);
				if (response.statusCode() >= 400) return apiError(response);
				Map<String, Object> parsed = parseResponse(response);
				Object certs = parsed.get("Certificates");
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
				String certDN = context.getParameter("certDN", "");
				Map<String, Object> body = Map.of("CertificateDN", certDN);
				HttpResponse<String> response = post(baseUrl + "/vedsdk/certificates/revoke", body, headers);
				return toResult(response);
			}
			case "download": {
				String certDN = context.getParameter("certDN", "");
				String format = context.getParameter("downloadFormat", "Base64");
				Map<String, Object> body = Map.of("CertificateDN", certDN, "Format", format);
				HttpResponse<String> response = post(baseUrl + "/vedsdk/certificates/retrieve", body, headers);
				return toResult(response);
			}
			case "renew": {
				String certDN = context.getParameter("certDN", "");
				Map<String, Object> body = Map.of("CertificateDN", certDN);
				HttpResponse<String> response = post(baseUrl + "/vedsdk/certificates/renew", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown certificate operation: " + operation);
		}
	}

	private NodeExecutionResult executePolicy(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String policyDN = context.getParameter("policyDN", "");
		Map<String, Object> body = Map.of("PolicyDN", policyDN);
		HttpResponse<String> response = post(baseUrl + "/vedsdk/certificates/checkpolicy", body, headers);
		return toResult(response);
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("url", ""));
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials, String baseUrl) throws Exception {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");

		// Authenticate with username/password to get token
		String token = String.valueOf(credentials.getOrDefault("token", ""));
		if (token.isEmpty()) {
			String username = String.valueOf(credentials.getOrDefault("username", ""));
			String password = String.valueOf(credentials.getOrDefault("password", ""));
			Map<String, Object> authBody = Map.of("Username", username, "Password", password);
			HttpResponse<String> authResponse = post(baseUrl + "/vedsdk/authorize", authBody, Map.of("Content-Type", "application/json"));
			if (authResponse.statusCode() < 400) {
				Map<String, Object> authParsed = parseResponse(authResponse);
				token = String.valueOf(authParsed.getOrDefault("APIKey", authParsed.getOrDefault("Token", "")));
			}
		}

		headers.put("Authorization", "Bearer " + token);
		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
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
		return NodeExecutionResult.error("Venafi Datacenter API error (HTTP " + response.statusCode() + "): " + body);
	}
}
