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
 * Venafi TLS Protect Datacenter Trigger Node -- polls for new or
 * expiring certificates via the Venafi TPP API.
 */
@Slf4j
@Node(
	type = "venafiTlsProtectDatacenterTrigger",
	displayName = "Venafi TLS Protect Datacenter Trigger",
	description = "Polls Venafi TLS Protect Datacenter for new or expiring certificates.",
	category = "Miscellaneous",
	icon = "venafi",
	trigger = true,
	polling = true,
	credentials = {"venafiTlsProtectDatacenterApi"},
	searchOnly = true,
	other = true
)
public class VenafiTlsProtectDatacenterTriggerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("triggerResource").displayName("Trigger On")
				.type(ParameterType.OPTIONS).required(true).defaultValue("newCertificate")
				.options(List.of(
					ParameterOption.builder().name("New Certificates").value("newCertificate")
						.description("Trigger when new certificates are created").build(),
					ParameterOption.builder().name("Expiring Certificates").value("expiringCertificate")
						.description("Trigger when certificates are about to expire").build()
				)).build(),

			NodeParameter.builder()
				.name("policyFolder").displayName("Policy Folder DN").type(ParameterType.STRING)
				.description("Policy folder to monitor, e.g. \\VED\\Policy\\MyApp").build(),

			NodeParameter.builder()
				.name("expirationDays").displayName("Days Before Expiration").type(ParameterType.NUMBER).defaultValue(30)
				.displayOptions(Map.of("show", Map.of("triggerResource", List.of("expiringCertificate"))))
				.build(),

			NodeParameter.builder()
				.name("limit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
				.description("Maximum number of items to return per poll.").build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String triggerResource = context.getParameter("triggerResource", "newCertificate");
		int limit = toInt(context.getParameter("limit", 50), 50);
		String policyFolder = context.getParameter("policyFolder", "");

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(credentials, baseUrl);

			long now = System.currentTimeMillis();
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTimestamp", now);

			Map<String, Object> qp = new LinkedHashMap<>();
			if (!policyFolder.isEmpty()) qp.put("ParentDnRecursive", policyFolder);
			qp.put("Limit", limit);

			HttpResponse<String> response = get(buildUrl(baseUrl + "/vedsdk/certificates", qp), headers);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Venafi Datacenter Trigger error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			Object certs = parsed.get("Certificates");

			Set<String> seenDNs = staticData.containsKey("seenDNs")
				? new HashSet<>((List<String>) staticData.get("seenDNs"))
				: new HashSet<>();

			List<Map<String, Object>> items = new ArrayList<>();
			Set<String> currentDNs = new HashSet<>();

			if (certs instanceof List) {
				for (Object c : (List<?>) certs) {
					if (c instanceof Map) {
						Map<String, Object> cert = (Map<String, Object>) c;
						String dn = String.valueOf(cert.getOrDefault("DN", ""));
						currentDNs.add(dn);

						if ("newCertificate".equals(triggerResource)) {
							if (!seenDNs.contains(dn)) {
								Map<String, Object> enriched = new LinkedHashMap<>(cert);
								enriched.put("_triggerTimestamp", System.currentTimeMillis());
								items.add(wrapInJson(enriched));
							}
						} else {
							// Expiring certificates
							Map<String, Object> enriched = new LinkedHashMap<>(cert);
							enriched.put("_triggerTimestamp", System.currentTimeMillis());
							enriched.put("_expirationDaysThreshold", toInt(context.getParameter("expirationDays", 30), 30));
							items.add(wrapInJson(enriched));
						}
					}
				}
			}

			newStaticData.put("seenDNs", new ArrayList<>(currentDNs));

			if (items.isEmpty()) {
				return NodeExecutionResult.builder().output(List.of(List.of())).staticData(newStaticData).build();
			}

			log.debug("Venafi Datacenter Trigger: found {} certificates", items.size());
			return NodeExecutionResult.builder().output(List.of(items)).staticData(newStaticData).build();

		} catch (Exception e) {
			return handleError(context, "Venafi Datacenter Trigger error: " + e.getMessage(), e);
		}
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
}
