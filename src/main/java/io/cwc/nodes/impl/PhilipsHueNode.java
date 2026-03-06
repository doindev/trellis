package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Philips Hue — control smart lights using the Philips Hue API via the Meethue cloud.
 */
@Node(
		type = "philipsHue",
		displayName = "Philips Hue",
		description = "Control Philips Hue smart lights",
		category = "Miscellaneous",
		icon = "philipsHue",
		credentials = {"philipsHueOAuth2Api"},
		searchOnly = true
)
public class PhilipsHueNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.meethue.com/route";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");

		String resource = context.getParameter("resource", "light");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// Discover or create n8n user on the bridge
				String user = discoverUser(headers);

				Map<String, Object> result = switch (resource) {
					case "light" -> handleLight(context, headers, user, operation);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	@SuppressWarnings("unchecked")
	private String discoverUser(Map<String, String> headers) throws Exception {
		// Get bridge config to find existing n8n user
		HttpResponse<String> configResponse = get(BASE_URL + "/api/0/config", headers);
		Map<String, Object> config = parseResponse(configResponse);
		Object whitelist = config.get("whitelist");
		if (whitelist instanceof Map) {
			Map<String, Object> whitelistMap = (Map<String, Object>) whitelist;
			for (Map.Entry<String, Object> entry : whitelistMap.entrySet()) {
				if (entry.getValue() instanceof Map) {
					Map<String, Object> userInfo = (Map<String, Object>) entry.getValue();
					String name = String.valueOf(userInfo.getOrDefault("name", ""));
					if ("n8n".equals(name)) {
						return entry.getKey();
					}
				}
			}
		}

		// No n8n user found, create one
		// Set linkbutton to allow user creation
		put(BASE_URL + "/api/0/config", Map.of("linkbutton", true), headers);

		// Create n8n user
		HttpResponse<String> createResponse = post(BASE_URL + "/api", Map.of("devicetype", "n8n"), headers);
		Map<String, Object> createData = parseResponse(createResponse);
		Object success = createData.get("success");
		if (success instanceof Map) {
			return String.valueOf(((Map<String, Object>) success).getOrDefault("username", ""));
		}

		throw new RuntimeException("Failed to create n8n user on Philips Hue bridge");
	}

	private Map<String, Object> handleLight(NodeExecutionContext context, Map<String, String> headers, String user, String operation) throws Exception {
		String apiBase = BASE_URL + "/api/" + user;
		return switch (operation) {
			case "delete" -> {
				String lightId = context.getParameter("lightId", "");
				HttpResponse<String> response = delete(apiBase + "/lights/" + encode(lightId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String lightId = context.getParameter("lightId", "");
				HttpResponse<String> response = get(apiBase + "/lights/" + encode(lightId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(apiBase + "/lights", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String lightId = context.getParameter("lightId", "");
				Map<String, Object> state = new LinkedHashMap<>();

				boolean on = toBoolean(context.getParameters().get("on"), true);
				state.put("on", on);

				int bri = toInt(context.getParameters().get("bri"), -1);
				if (bri >= 0) state.put("bri", bri);

				int hue = toInt(context.getParameters().get("hue"), -1);
				if (hue >= 0) state.put("hue", hue);

				int sat = toInt(context.getParameters().get("sat"), -1);
				if (sat >= 0) state.put("sat", sat);

				int ct = toInt(context.getParameters().get("ct"), -1);
				if (ct >= 0) state.put("ct", ct);

				String xy = context.getParameter("xy", "");
				if (!xy.isEmpty()) {
					String[] parts = xy.split("\\s*,\\s*");
					if (parts.length == 2) {
						state.put("xy", List.of(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
					}
				}

				String alert = context.getParameter("alert", "");
				if (!alert.isEmpty()) state.put("alert", alert);

				String effect = context.getParameter("effect", "");
				if (!effect.isEmpty()) state.put("effect", effect);

				double transitiontime = toDouble(context.getParameters().get("transitiontime"), -1.0);
				if (transitiontime >= 0) state.put("transitiontime", (int) (transitiontime * 100));

				int briInc = toInt(context.getParameters().get("briInc"), 0);
				if (briInc != 0) state.put("bri_inc", briInc);

				int hueInc = toInt(context.getParameters().get("hueInc"), 0);
				if (hueInc != 0) state.put("hue_inc", hueInc);

				int satInc = toInt(context.getParameters().get("satInc"), 0);
				if (satInc != 0) state.put("sat_inc", satInc);

				int ctInc = toInt(context.getParameters().get("ctInc"), 0);
				if (ctInc != 0) state.put("ct_inc", ctInc);

				HttpResponse<String> response = put(apiBase + "/lights/" + encode(lightId) + "/state", state, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown light operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("light")
						.options(List.of(
								ParameterOption.builder().name("Light").value("light").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("lightId").displayName("Light ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the light.").build(),
				NodeParameter.builder()
						.name("on").displayName("On")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Whether the light is on.").build(),
				NodeParameter.builder()
						.name("bri").displayName("Brightness")
						.type(ParameterType.NUMBER).defaultValue(-1)
						.description("Brightness value (1-254).").build(),
				NodeParameter.builder()
						.name("hue").displayName("Hue")
						.type(ParameterType.NUMBER).defaultValue(-1)
						.description("Hue value (0-65535).").build(),
				NodeParameter.builder()
						.name("sat").displayName("Saturation")
						.type(ParameterType.NUMBER).defaultValue(-1)
						.description("Saturation value (0-254).").build(),
				NodeParameter.builder()
						.name("ct").displayName("Color Temperature")
						.type(ParameterType.NUMBER).defaultValue(-1)
						.description("Color temperature in Mired (153-500).").build(),
				NodeParameter.builder()
						.name("xy").displayName("XY Color")
						.type(ParameterType.STRING).defaultValue("")
						.description("CIE color coordinates as 'x,y' (e.g., 0.3, 0.3).").build(),
				NodeParameter.builder()
						.name("alert").displayName("Alert")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("None").value("none").build(),
								ParameterOption.builder().name("Select").value("select").build(),
								ParameterOption.builder().name("Long Select").value("lselect").build()
						))
						.description("Alert effect.").build(),
				NodeParameter.builder()
						.name("effect").displayName("Effect")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("None").value("none").build(),
								ParameterOption.builder().name("Color Loop").value("colorloop").build()
						))
						.description("Dynamic effect.").build(),
				NodeParameter.builder()
						.name("transitiontime").displayName("Transition Time")
						.type(ParameterType.NUMBER).defaultValue(-1)
						.description("Transition time in seconds.").build(),
				NodeParameter.builder()
						.name("briInc").displayName("Brightness Increment")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Brightness increment (-254 to 254).").build(),
				NodeParameter.builder()
						.name("hueInc").displayName("Hue Increment")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Hue increment (-65534 to 65534).").build(),
				NodeParameter.builder()
						.name("satInc").displayName("Saturation Increment")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Saturation increment (-254 to 254).").build(),
				NodeParameter.builder()
						.name("ctInc").displayName("Color Temp Increment")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Color temperature increment (-65534 to 65534).").build()
		);
	}
}
