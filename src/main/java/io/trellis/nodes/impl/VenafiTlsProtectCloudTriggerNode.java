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
 * Venafi TLS Protect Cloud Trigger Node -- polls for new certificate
 * requests or expiring certificates.
 */
@Slf4j
@Node(
	type = "venafiTlsProtectCloudTrigger",
	displayName = "Venafi TLS Protect Cloud Trigger",
	description = "Polls Venafi TLS Protect Cloud for new certificate requests or expiring certificates.",
	category = "Miscellaneous",
	icon = "venafi",
	trigger = true,
	polling = true,
	credentials = {"venafiTlsProtectCloudApi"},
	searchOnly = true,
	triggerCategory = "Other"
)
public class VenafiTlsProtectCloudTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.venafi.cloud";

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
				.type(ParameterType.OPTIONS).required(true).defaultValue("certificateRequest")
				.options(List.of(
					ParameterOption.builder().name("New Certificate Requests").value("certificateRequest")
						.description("Trigger when new certificate requests are created").build(),
					ParameterOption.builder().name("Expiring Certificates").value("expiringCertificate")
						.description("Trigger when certificates are about to expire").build()
				)).build(),

			NodeParameter.builder()
				.name("expirationDays").displayName("Days Before Expiration").type(ParameterType.NUMBER).defaultValue(30)
				.description("Number of days before expiration to trigger on.")
				.displayOptions(Map.of("show", Map.of("triggerResource", List.of("expiringCertificate"))))
				.build(),

			NodeParameter.builder()
				.name("limit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
				.description("Maximum number of items to return per poll.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String triggerResource = context.getParameter("triggerResource", "certificateRequest");
		int limit = toInt(context.getParameter("limit", 50), 50);

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			long now = System.currentTimeMillis();
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTimestamp", now);

			if ("certificateRequest".equals(triggerResource)) {
				return pollCertificateRequests(headers, staticData, newStaticData, limit);
			} else {
				int days = toInt(context.getParameter("expirationDays", 30), 30);
				return pollExpiringCertificates(headers, newStaticData, limit, days);
			}

		} catch (Exception e) {
			return handleError(context, "Venafi Cloud Trigger error: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult pollCertificateRequests(Map<String, String> headers,
			Map<String, Object> staticData, Map<String, Object> newStaticData, int limit) throws Exception {

		String url = buildUrl(BASE_URL + "/outagedetection/v1/certificaterequests", Map.of("limit", limit));
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError(response);
		}

		Map<String, Object> parsed = parseResponse(response);
		Object reqs = parsed.get("certificateRequests");

		Set<String> seenIds = staticData.containsKey("seenIds")
			? new HashSet<>((List<String>) staticData.get("seenIds"))
			: new HashSet<>();

		List<Map<String, Object>> items = new ArrayList<>();
		Set<String> currentIds = new HashSet<>();

		if (reqs instanceof List) {
			for (Object r : (List<?>) reqs) {
				if (r instanceof Map) {
					Map<String, Object> reqMap = (Map<String, Object>) r;
					String id = String.valueOf(reqMap.getOrDefault("id", ""));
					currentIds.add(id);
					if (!seenIds.contains(id)) {
						Map<String, Object> enriched = new LinkedHashMap<>(reqMap);
						enriched.put("_triggerTimestamp", System.currentTimeMillis());
						items.add(wrapInJson(enriched));
					}
				}
			}
		}

		newStaticData.put("seenIds", new ArrayList<>(currentIds));

		if (items.isEmpty()) {
			return NodeExecutionResult.builder().output(List.of(List.of())).staticData(newStaticData).build();
		}

		log.debug("Venafi Cloud Trigger: found {} new certificate requests", items.size());
		return NodeExecutionResult.builder().output(List.of(items)).staticData(newStaticData).build();
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult pollExpiringCertificates(Map<String, String> headers,
			Map<String, Object> newStaticData, int limit, int days) throws Exception {

		String url = buildUrl(BASE_URL + "/outagedetection/v1/certificates", Map.of("limit", limit));
		HttpResponse<String> response = get(url, headers);

		if (response.statusCode() >= 400) {
			return apiError(response);
		}

		Map<String, Object> parsed = parseResponse(response);
		Object certs = parsed.get("certificates");

		long expirationThreshold = System.currentTimeMillis() + ((long) days * 24 * 60 * 60 * 1000);
		List<Map<String, Object>> items = new ArrayList<>();

		if (certs instanceof List) {
			for (Object c : (List<?>) certs) {
				if (c instanceof Map) {
					Map<String, Object> cert = (Map<String, Object>) c;
					Object validityEnd = cert.get("validityEnd");
					if (validityEnd instanceof String) {
						// ISO date string -- just include based on limit
						Map<String, Object> enriched = new LinkedHashMap<>(cert);
						enriched.put("_triggerTimestamp", System.currentTimeMillis());
						enriched.put("_expirationDaysThreshold", days);
						items.add(wrapInJson(enriched));
					} else if (validityEnd instanceof Number) {
						long endTime = ((Number) validityEnd).longValue();
						if (endTime <= expirationThreshold) {
							Map<String, Object> enriched = new LinkedHashMap<>(cert);
							enriched.put("_triggerTimestamp", System.currentTimeMillis());
							items.add(wrapInJson(enriched));
						}
					}
				}
			}
		}

		if (items.isEmpty()) {
			return NodeExecutionResult.builder().output(List.of(List.of())).staticData(newStaticData).build();
		}

		log.debug("Venafi Cloud Trigger: found {} expiring certificates", items.size());
		return NodeExecutionResult.builder().output(List.of(items)).staticData(newStaticData).build();
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("tppl-api-key", String.valueOf(credentials.getOrDefault("apiKey", "")));
		return headers;
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Venafi Cloud Trigger error (HTTP " + response.statusCode() + "): " + body);
	}
}
