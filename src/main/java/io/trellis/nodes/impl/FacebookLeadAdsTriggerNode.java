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
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "facebookLeadAdsTrigger",
	displayName = "Facebook Lead Ads Trigger",
	description = "Triggers when a new lead is generated from a Facebook Lead Ad.",
	category = "Social Media",
	icon = "facebook",
	credentials = {"facebookLeadAdsOAuth2Api"},
	trigger = true,
	searchOnly = true,
	other = true
)
public class FacebookLeadAdsTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://graph.facebook.com/v17.0";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("pageId").displayName("Page ID")
				.type(ParameterType.STRING).required(true)
				.description("The Facebook Page ID to monitor for lead ads.")
				.placeHolder("123456789012345")
				.build(),

			NodeParameter.builder()
				.name("formId").displayName("Form ID")
				.type(ParameterType.STRING)
				.description("Optionally filter by a specific lead form ID. Leave empty for all forms.")
				.placeHolder("123456789012345")
				.build(),

			NodeParameter.builder()
				.name("fetchLeadDetails").displayName("Fetch Lead Details")
				.type(ParameterType.BOOLEAN).defaultValue(true)
				.description("Whether to fetch the full lead details including field data.")
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		String pageId = context.getParameter("pageId", "");
		String formId = context.getParameter("formId", "");
		boolean fetchDetails = toBoolean(context.getParameter("fetchLeadDetails", true), true);

		try {
			Map<String, String> headers = Map.of("Content-Type", "application/json");

			// Build URL to get leads
			String url;
			if (formId != null && !formId.isEmpty()) {
				url = BASE_URL + "/" + encode(formId) + "/leads?access_token=" + encode(accessToken);
			} else {
				url = BASE_URL + "/" + encode(pageId) + "/leadgen_forms?access_token=" + encode(accessToken)
					+ "&fields=id,name,leads{created_time,id,field_data}";
			}

			// Track last seen lead
			String lastLeadId = staticData.containsKey("lastLeadId")
				? String.valueOf(staticData.get("lastLeadId"))
				: "";

			HttpResponse<String> response = get(url, headers);
			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Facebook Lead Ads API error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> parsed = parseResponse(response);
			List<Map<String, Object>> items = new ArrayList<>();
			String newestLeadId = lastLeadId;

			// Extract leads from response
			Object dataObj = parsed.get("data");
			List<Map<String, Object>> leads = new ArrayList<>();

			if (dataObj instanceof List) {
				List<?> dataList = (List<?>) dataObj;
				if (formId != null && !formId.isEmpty()) {
					// Direct leads response
					for (Object item : dataList) {
						if (item instanceof Map) {
							leads.add((Map<String, Object>) item);
						}
					}
				} else {
					// Forms response - extract leads from each form
					for (Object form : dataList) {
						if (form instanceof Map) {
							Map<String, Object> formMap = (Map<String, Object>) form;
							Object leadsObj = formMap.get("leads");
							if (leadsObj instanceof Map) {
								Object leadsData = ((Map<String, Object>) leadsObj).get("data");
								if (leadsData instanceof List) {
									for (Object lead : (List<?>) leadsData) {
										if (lead instanceof Map) {
											Map<String, Object> leadMap = new LinkedHashMap<>((Map<String, Object>) lead);
											leadMap.put("formId", formMap.get("id"));
											leadMap.put("formName", formMap.get("name"));
											leads.add(leadMap);
										}
									}
								}
							}
						}
					}
				}
			}

			// Filter new leads
			boolean foundLast = lastLeadId.isEmpty();
			for (Map<String, Object> lead : leads) {
				String leadId = String.valueOf(lead.getOrDefault("id", ""));

				if (!foundLast) {
					if (leadId.equals(lastLeadId)) {
						foundLast = true;
					}
					continue;
				}

				// Fetch full lead details if requested
				Map<String, Object> leadData = lead;
				if (fetchDetails && !leadId.isEmpty()) {
					try {
						String detailUrl = BASE_URL + "/" + encode(leadId) + "?access_token=" + encode(accessToken)
							+ "&fields=id,created_time,field_data,ad_id,ad_name,adset_id,adset_name,campaign_id,campaign_name,form_id,is_organic";
						HttpResponse<String> detailResponse = get(detailUrl, headers);
						if (detailResponse.statusCode() < 400) {
							leadData = parseResponse(detailResponse);
						}
					} catch (Exception e) {
						log.warn("Failed to fetch lead details for {}: {}", leadId, e.getMessage());
					}
				}

				Map<String, Object> triggerItem = new LinkedHashMap<>(leadData);
				triggerItem.put("_triggerTimestamp", System.currentTimeMillis());
				items.add(wrapInJson(triggerItem));

				if (newestLeadId.isEmpty() || leadId.compareTo(newestLeadId) > 0) {
					newestLeadId = leadId;
				}
			}

			// Update static data
			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			if (!newestLeadId.isEmpty()) {
				newStaticData.put("lastLeadId", newestLeadId);
			}

			if (items.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(newStaticData)
					.build();
			}

			log.debug("Facebook Lead Ads trigger: found {} new leads", items.size());
			return NodeExecutionResult.builder()
				.output(List.of(items))
				.staticData(newStaticData)
				.build();
		} catch (Exception e) {
			return handleError(context, "Facebook Lead Ads Trigger error: " + e.getMessage(), e);
		}
	}
}
