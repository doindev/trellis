package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
 * Sentiment Node - lexicon-based sentiment analysis using an AFINN-style word list.
 * No external API or credentials required. Tokenizes text, looks up each word in a
 * built-in lexicon (~180 words scored -5 to +5), and produces a normalized score.
 */
@Slf4j
@Node(
	type = "sentiment",
	displayName = "Sentiment Analyzer",
	description = "Analyze text sentiment using a built-in word lexicon. Produces a score from -1 (negative) to +1 (positive) with no external API required.",
	category = "Data Transformation",
	icon = "heart-pulse"
)
public class SentimentNode extends AbstractNode {

	private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z']+");

	/** AFINN-style lexicon: word -> score (-5 to +5) */
	private static final Map<String, Integer> LEXICON = buildLexicon();

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
				.description("The field containing text to analyze. Supports dot notation.")
				.type(ParameterType.STRING)
				.defaultValue("body")
				.required(true)
				.build(),
			NodeParameter.builder()
				.name("outputField")
				.displayName("Output Field")
				.description("The field to store the sentiment result object.")
				.type(ParameterType.STRING)
				.defaultValue("sentiment")
				.required(true)
				.build(),
			NodeParameter.builder()
				.name("positiveThreshold")
				.displayName("Positive Threshold")
				.description("Score above this value is labeled 'positive'.")
				.type(ParameterType.NUMBER)
				.defaultValue(0.05)
				.build(),
			NodeParameter.builder()
				.name("negativeThreshold")
				.displayName("Negative Threshold")
				.description("Score below this value is labeled 'negative'.")
				.type(ParameterType.NUMBER)
				.defaultValue(-0.05)
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		String inputField = context.getParameter("inputField", "body");
		String outputField = context.getParameter("outputField", "sentiment");
		double positiveThreshold = toDouble(context.getParameter("positiveThreshold", 0.05), 0.05);
		double negativeThreshold = toDouble(context.getParameter("negativeThreshold", -0.05), -0.05);

		List<Map<String, Object>> outputItems = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = deepClone(item);
				Object textObj = getNestedValue(item, "json." + inputField);
				String text = textObj != null ? String.valueOf(textObj) : "";

				Map<String, Object> sentimentResult = analyzeSentiment(text, positiveThreshold, negativeThreshold);
				setNestedValue(result, "json." + outputField, sentimentResult);
				outputItems.add(result);
			} catch (Exception e) {
				return handleError(context, "Error analyzing sentiment: " + e.getMessage(), e);
			}
		}

		log.debug("Sentiment: analyzed {} items", outputItems.size());
		return NodeExecutionResult.success(outputItems);
	}

	private Map<String, Object> analyzeSentiment(String text, double positiveThreshold, double negativeThreshold) {
		var matcher = WORD_PATTERN.matcher(text.toLowerCase());

		int wordCount = 0;
		int matchedWords = 0;
		int totalScore = 0;

		while (matcher.find()) {
			wordCount++;
			String word = matcher.group();
			Integer score = LEXICON.get(word);
			if (score != null) {
				matchedWords++;
				totalScore += score;
			}
		}

		// Normalize to [-1, 1] range
		double normalizedScore = matchedWords > 0 ? (double) totalScore / (matchedWords * 5.0) : 0.0;
		normalizedScore = Math.max(-1.0, Math.min(1.0, normalizedScore));

		String label;
		if (normalizedScore > positiveThreshold) {
			label = "positive";
		} else if (normalizedScore < negativeThreshold) {
			label = "negative";
		} else {
			label = "neutral";
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("score", Math.round(normalizedScore * 10000.0) / 10000.0);
		result.put("label", label);
		result.put("wordCount", wordCount);
		result.put("matchedWords", matchedWords);
		result.put("magnitude", Math.round(Math.abs(normalizedScore) * 10000.0) / 10000.0);
		return result;
	}

	@SuppressWarnings("java:S3776")
	private static Map<String, Integer> buildLexicon() {
		Map<String, Integer> lex = new LinkedHashMap<>();

		// Strong positive (+5)
		lex.put("outstanding", 5); lex.put("superb", 5); lex.put("breathtaking", 5);
		lex.put("thrilling", 5); lex.put("phenomenal", 5);

		// Positive (+4)
		lex.put("amazing", 4); lex.put("awesome", 4); lex.put("brilliant", 4);
		lex.put("excellent", 4); lex.put("fantastic", 4); lex.put("incredible", 4);
		lex.put("magnificent", 4); lex.put("marvelous", 4); lex.put("spectacular", 4);
		lex.put("wonderful", 4); lex.put("delightful", 4); lex.put("exceptional", 4);

		// Positive (+3)
		lex.put("beautiful", 3); lex.put("charming", 3); lex.put("elegant", 3);
		lex.put("exciting", 3); lex.put("fabulous", 3); lex.put("glorious", 3);
		lex.put("gorgeous", 3); lex.put("great", 3); lex.put("impressive", 3);
		lex.put("inspiring", 3); lex.put("joyful", 3); lex.put("lovely", 3);
		lex.put("perfect", 3); lex.put("remarkable", 3); lex.put("splendid", 3);
		lex.put("stunning", 3); lex.put("terrific", 3); lex.put("triumphant", 3);

		// Positive (+2)
		lex.put("cheerful", 2); lex.put("comfortable", 2); lex.put("confident", 2);
		lex.put("creative", 2); lex.put("eager", 2); lex.put("effective", 2);
		lex.put("enjoyable", 2); lex.put("enthusiastic", 2); lex.put("favorable", 2);
		lex.put("friendly", 2); lex.put("fun", 2); lex.put("generous", 2);
		lex.put("glad", 2); lex.put("grateful", 2); lex.put("happy", 2);
		lex.put("healthy", 2); lex.put("helpful", 2); lex.put("honest", 2);
		lex.put("innovative", 2); lex.put("kind", 2); lex.put("loyal", 2);
		lex.put("pleasant", 2); lex.put("positive", 2); lex.put("powerful", 2);
		lex.put("proud", 2); lex.put("reliable", 2); lex.put("satisfied", 2);
		lex.put("smart", 2); lex.put("successful", 2); lex.put("supportive", 2);
		lex.put("thankful", 2); lex.put("thriving", 2); lex.put("valuable", 2);
		lex.put("vibrant", 2); lex.put("warm", 2); lex.put("welcoming", 2);

		// Mildly positive (+1)
		lex.put("adequate", 1); lex.put("agree", 1); lex.put("approve", 1);
		lex.put("calm", 1); lex.put("clean", 1); lex.put("cool", 1);
		lex.put("decent", 1); lex.put("easy", 1); lex.put("fair", 1);
		lex.put("fine", 1); lex.put("good", 1); lex.put("handy", 1);
		lex.put("hope", 1); lex.put("interesting", 1); lex.put("like", 1);
		lex.put("nice", 1); lex.put("ok", 1); lex.put("okay", 1);
		lex.put("pretty", 1); lex.put("safe", 1); lex.put("secure", 1);
		lex.put("simple", 1); lex.put("smooth", 1); lex.put("solid", 1);
		lex.put("stable", 1); lex.put("suitable", 1); lex.put("sure", 1);
		lex.put("sweet", 1); lex.put("thanks", 1); lex.put("useful", 1);
		lex.put("welcome", 1); lex.put("well", 1); lex.put("yes", 1);
		lex.put("love", 1);

		// Mildly negative (-1)
		lex.put("awkward", -1); lex.put("boring", -1); lex.put("complicated", -1);
		lex.put("confusing", -1); lex.put("delay", -1); lex.put("difficult", -1);
		lex.put("dislike", -1); lex.put("doubt", -1); lex.put("dull", -1);
		lex.put("hard", -1); lex.put("issue", -1); lex.put("lack", -1);
		lex.put("mediocre", -1); lex.put("miss", -1); lex.put("odd", -1);
		lex.put("poor", -1); lex.put("problem", -1); lex.put("slow", -1);
		lex.put("tired", -1); lex.put("tough", -1); lex.put("trouble", -1);
		lex.put("uncertain", -1); lex.put("unclear", -1); lex.put("unfair", -1);
		lex.put("unfortunately", -1); lex.put("unhappy", -1); lex.put("unlikely", -1);
		lex.put("weak", -1); lex.put("worry", -1); lex.put("wrong", -1);

		// Negative (-2)
		lex.put("angry", -2); lex.put("annoying", -2); lex.put("bad", -2);
		lex.put("broken", -2); lex.put("complaint", -2); lex.put("cruel", -2);
		lex.put("damage", -2); lex.put("dangerous", -2); lex.put("disappointed", -2);
		lex.put("disgusting", -2); lex.put("fail", -2); lex.put("failure", -2);
		lex.put("fear", -2); lex.put("frustrating", -2); lex.put("guilty", -2);
		lex.put("harm", -2); lex.put("hate", -2); lex.put("hostile", -2);
		lex.put("hurt", -2); lex.put("inferior", -2); lex.put("insult", -2);
		lex.put("nasty", -2); lex.put("negative", -2); lex.put("offensive", -2);
		lex.put("painful", -2); lex.put("reject", -2); lex.put("rude", -2);
		lex.put("sad", -2); lex.put("scary", -2); lex.put("sick", -2);
		lex.put("sorry", -2); lex.put("stupid", -2); lex.put("terrible", -2);
		lex.put("ugly", -2); lex.put("upset", -2); lex.put("useless", -2);
		lex.put("violent", -2); lex.put("waste", -2); lex.put("worse", -2);

		// Negative (-3)
		lex.put("abusive", -3); lex.put("awful", -3); lex.put("catastrophe", -3);
		lex.put("corrupt", -3); lex.put("destroy", -3); lex.put("devastating", -3);
		lex.put("disaster", -3); lex.put("dreadful", -3); lex.put("evil", -3);
		lex.put("horrible", -3); lex.put("miserable", -3); lex.put("nightmare", -3);
		lex.put("outrageous", -3); lex.put("pathetic", -3); lex.put("repulsive", -3);
		lex.put("revolting", -3); lex.put("shameful", -3); lex.put("toxic", -3);
		lex.put("tragic", -3); lex.put("worst", -3);

		// Strong negative (-4)
		lex.put("appalling", -4); lex.put("atrocious", -4); lex.put("despicable", -4);
		lex.put("disgraceful", -4); lex.put("horrendous", -4); lex.put("horrific", -4);
		lex.put("loathsome", -4); lex.put("monstrous", -4); lex.put("vile", -4);
		lex.put("wretched", -4);

		// Strongest negative (-5)
		lex.put("abhorrent", -5); lex.put("abominable", -5); lex.put("detestable", -5);
		lex.put("heinous", -5); lex.put("reprehensible", -5);

		return Map.copyOf(lex);
	}
}
