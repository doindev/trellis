package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Content Classifier Node - rules-based multi-label categorization using keyword lists.
 * Each rule defines a label and a set of keywords. Text is matched against each rule
 * to produce one or more category labels per item.
 */
@Slf4j
@Node(
	type = "contentClassifier",
	displayName = "Content Classifier",
	description = "Classify text into categories using keyword-based rules. Supports multi-label classification with configurable keyword lists.",
	category = "Data Transformation",
	icon = "tags"
)
public class ContentClassifierNode extends AbstractNode {

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
		return List.of(
			NodeParameter.builder()
				.name("inputField")
				.displayName("Input Field")
				.description("The field containing text to classify. Supports dot notation.")
				.type(ParameterType.STRING)
				.defaultValue("body")
				.required(true)
				.build(),
			NodeParameter.builder()
				.name("rules")
				.displayName("Classification Rules")
				.description("Define rules with labels and keyword lists. Each rule matches if any of its keywords appear in the text.")
				.type(ParameterType.FIXED_COLLECTION)
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("label")
						.displayName("Label")
						.description("The category label (e.g., 'Technology', 'Sports').")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. Technology")
						.build(),
					NodeParameter.builder()
						.name("keywords")
						.displayName("Keywords")
						.description("Comma-separated keywords to match (e.g., 'software,api,code,programming').")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. software,api,code")
						.build(),
					NodeParameter.builder()
						.name("caseSensitive")
						.displayName("Case Sensitive")
						.description("Whether keyword matching should be case-sensitive.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build()
				))
				.build(),
			NodeParameter.builder()
				.name("multiLabel")
				.displayName("Multi-Label")
				.description("Allow multiple labels per item. When disabled, only the best-matching label is used.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(true)
				.build(),
			NodeParameter.builder()
				.name("defaultLabel")
				.displayName("Default Label")
				.description("Label to apply when no rules match.")
				.type(ParameterType.STRING)
				.defaultValue("uncategorized")
				.build(),
			NodeParameter.builder()
				.name("outputField")
				.displayName("Output Field")
				.description("The field to store classification results.")
				.type(ParameterType.STRING)
				.defaultValue("categories")
				.build(),
			NodeParameter.builder()
				.name("includeScores")
				.displayName("Include Scores")
				.description("Include keyword match counts per label in the output.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String inputField = context.getParameter("inputField", "body");
		String outputField = context.getParameter("outputField", "categories");
		String defaultLabel = context.getParameter("defaultLabel", "uncategorized");
		boolean multiLabel = toBoolean(context.getParameter("multiLabel", true), true);
		boolean includeScores = toBoolean(context.getParameter("includeScores", false), false);

		List<ClassificationRule> rules = parseRules(context.getParameter("rules", null));

		List<Map<String, Object>> outputItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = deepClone(item);
				Object textObj = getNestedValue(item, "json." + inputField);
				String text = textObj != null ? String.valueOf(textObj) : "";

				Object classification = classify(text, rules, multiLabel, defaultLabel, includeScores);
				setNestedValue(result, "json." + outputField, classification);
				outputItems.add(result);
			} catch (Exception e) {
				return handleError(context, "Error classifying content: " + e.getMessage(), e);
			}
		}

		log.debug("ContentClassifier: classified {} items using {} rules", outputItems.size(), rules.size());
		return NodeExecutionResult.success(outputItems);
	}

	private Object classify(String text, List<ClassificationRule> rules, boolean multiLabel,
			String defaultLabel, boolean includeScores) {
		List<LabelMatch> matches = new ArrayList<>();

		for (ClassificationRule rule : rules) {
			String compareText = rule.caseSensitive ? text : text.toLowerCase();
			int matchCount = 0;
			for (String keyword : rule.keywords) {
				String compareKeyword = rule.caseSensitive ? keyword : keyword.toLowerCase();
				if (compareText.contains(compareKeyword)) {
					matchCount++;
				}
			}
			if (matchCount > 0) {
				matches.add(new LabelMatch(rule.label, matchCount));
			}
		}

		if (matches.isEmpty()) {
			if (includeScores) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("label", defaultLabel);
				entry.put("matchCount", 0);
				return List.of(entry);
			}
			return List.of(defaultLabel);
		}

		// Sort by match count descending
		matches.sort((a, b) -> Integer.compare(b.matchCount, a.matchCount));

		if (!multiLabel) {
			// Keep only the best match
			matches = List.of(matches.get(0));
		}

		if (includeScores) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (LabelMatch m : matches) {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("label", m.label);
				entry.put("matchCount", m.matchCount);
				results.add(entry);
			}
			return results;
		}

		List<String> labels = new ArrayList<>();
		for (LabelMatch m : matches) {
			labels.add(m.label);
		}
		return labels;
	}

	@SuppressWarnings("unchecked")
	private List<ClassificationRule> parseRules(Object rulesParam) {
		List<ClassificationRule> rules = new ArrayList<>();
		if (rulesParam == null) return rules;

		List<?> ruleList;
		if (rulesParam instanceof Map) {
			Object values = ((Map<String, Object>) rulesParam).get("values");
			if (values instanceof List) {
				ruleList = (List<?>) values;
			} else {
				ruleList = List.of(rulesParam);
			}
		} else if (rulesParam instanceof List) {
			ruleList = (List<?>) rulesParam;
		} else {
			return rules;
		}

		for (Object entry : ruleList) {
			if (entry instanceof Map) {
				Map<String, Object> map = (Map<String, Object>) entry;
				String label = toString(map.get("label"));
				String keywordsStr = toString(map.get("keywords"));
				boolean caseSensitive = toBoolean(map.get("caseSensitive"), false);

				if (label != null && !label.isBlank() && keywordsStr != null && !keywordsStr.isBlank()) {
					List<String> keywords = new ArrayList<>();
					for (String kw : keywordsStr.split(",")) {
						String trimmed = kw.trim();
						if (!trimmed.isEmpty()) {
							keywords.add(trimmed);
						}
					}
					if (!keywords.isEmpty()) {
						rules.add(new ClassificationRule(label.trim(), keywords, caseSensitive));
					}
				}
			}
		}
		return rules;
	}

	private record ClassificationRule(String label, List<String> keywords, boolean caseSensitive) {}
	private record LabelMatch(String label, int matchCount) {}
}
