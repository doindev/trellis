package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.time.Instant;
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
 * Google Business Profile Trigger -- polls for new or updated reviews
 * on a Google Business Profile location.
 */
@Slf4j
@Node(
	type = "googleBusinessProfileTrigger",
	displayName = "Google Business Profile Trigger",
	description = "Polls for new or updated reviews on a Google Business Profile",
	category = "Google",
	icon = "googleBusinessProfile",
	trigger = true,
	polling = true,
	credentials = {"googleBusinessProfileOAuth2Api"}
)
public class GoogleBusinessProfileTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://mybusiness.googleapis.com/v4";

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
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("account").displayName("Account")
			.type(ParameterType.STRING).required(true)
			.description("The Google Business Profile account name (e.g., 'accounts/123456789').")
			.placeHolder("accounts/123456789")
			.build());

		params.add(NodeParameter.builder()
			.name("location").displayName("Location")
			.type(ParameterType.STRING).required(true)
			.description("The location name (e.g., 'locations/987654321').")
			.placeHolder("locations/987654321")
			.build());

		params.add(NodeParameter.builder()
			.name("triggerOn").displayName("Trigger On")
			.type(ParameterType.OPTIONS).required(true).defaultValue("newReviews")
			.options(List.of(
				ParameterOption.builder().name("New Reviews").value("newReviews")
					.description("Trigger when new reviews are posted").build(),
				ParameterOption.builder().name("Updated Reviews").value("updatedReviews")
					.description("Trigger when reviews are updated").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.nestedParameters(List.of(
				NodeParameter.builder().name("pageSize").displayName("Page Size")
					.type(ParameterType.NUMBER).defaultValue(50)
					.description("Maximum number of reviews to return per poll.").build()
			)).build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		Map<String, Object> staticData = context.getStaticData();
		if (staticData == null) staticData = new LinkedHashMap<>();

		try {
			String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));
			Map<String, String> headers = getAuthHeaders(accessToken);

			String account = context.getParameter("account", "");
			String location = context.getParameter("location", "");
			String triggerOn = context.getParameter("triggerOn", "newReviews");
			Map<String, Object> additionalFields = context.getParameter("additionalFields", Map.of());

			String now = Instant.now().toString();
			String lastPollTime = (String) staticData.getOrDefault("lastPollTime",
				Instant.now().minusSeconds(300).toString());

			int pageSize = toInt(additionalFields.get("pageSize"), 50);

			// Build reviews URL
			String url = BASE_URL + "/" + account + "/" + location + "/reviews?pageSize=" + pageSize;

			HttpResponse<String> response = get(url, headers);

			Map<String, Object> newStaticData = new LinkedHashMap<>(staticData);
			newStaticData.put("lastPollTime", now);

			if (response.statusCode() >= 400) {
				String body = response.body() != null ? response.body() : "";
				if (body.length() > 300) body = body.substring(0, 300) + "...";
				return NodeExecutionResult.error("Google Business Profile API error (HTTP " + response.statusCode() + "): " + body);
			}

			Map<String, Object> result = parseResponse(response);
			Object reviews = result.get("reviews");

			if (reviews instanceof List) {
				List<Map<String, Object>> reviewItems = new ArrayList<>();
				for (Object review : (List<?>) reviews) {
					if (review instanceof Map) {
						Map<String, Object> reviewMap = (Map<String, Object>) review;

						// Filter based on trigger type and last poll time
						String updateTime = getReviewTimestamp(reviewMap, triggerOn);
						if (updateTime != null && updateTime.compareTo(lastPollTime) > 0) {
							Map<String, Object> enriched = new LinkedHashMap<>(reviewMap);
							enriched.put("_triggerTimestamp", System.currentTimeMillis());
							enriched.put("_triggerEvent", triggerOn);
							reviewItems.add(wrapInJson(enriched));
						}
					}
				}

				if (reviewItems.isEmpty()) {
					return NodeExecutionResult.builder()
						.output(List.of(List.of()))
						.staticData(newStaticData)
						.build();
				}

				log.debug("Google Business Profile trigger: found {} reviews", reviewItems.size());
				return NodeExecutionResult.builder()
					.output(List.of(reviewItems))
					.staticData(newStaticData)
					.build();
			}

			return NodeExecutionResult.builder()
				.output(List.of(List.of()))
				.staticData(newStaticData)
				.build();
		} catch (Exception e) {
			return handleError(context, "Google Business Profile Trigger error: " + e.getMessage(), e);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private String getReviewTimestamp(Map<String, Object> review, String triggerOn) {
		if ("updatedReviews".equals(triggerOn)) {
			Object updateTime = review.get("updateTime");
			return updateTime != null ? String.valueOf(updateTime) : null;
		}
		// For new reviews, use createTime
		Object createTime = review.get("createTime");
		return createTime != null ? String.valueOf(createTime) : null;
	}
}
