package io.trellis.nodes.impl.ai;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractAiSubNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Node(
		type = "outputParserItemList",
		displayName = "Item List Output Parser",
		description = "Return the results as separate items",
		category = "AI / Output Parsers",
		icon = "list-end"
)
public class ItemListOutputParserNode extends AbstractAiSubNode {

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int numberOfItems = toInt(context.getParameter("numberOfItems", -1), -1);
		String separator = context.getParameter("separator", "\\n");

		return new ItemListOutputParser(
				numberOfItems > 0 ? numberOfItems : null,
				"\\n".equals(separator) ? "\n" : separator
		);
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(
				NodeOutput.builder()
						.name("ai_outputParser")
						.displayName("Output Parser")
						.type(NodeOutput.OutputType.AI_OUTPUT_PARSER)
						.build()
		);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("numberOfItems").displayName("Number Of Items")
						.type(ParameterType.NUMBER)
						.defaultValue(-1)
						.description("Maximum number of items to return. Set to -1 for no limit.")
						.build(),
				NodeParameter.builder()
						.name("separator").displayName("Separator")
						.type(ParameterType.STRING)
						.defaultValue("\\n")
						.description("The separator used to split results into separate items. " +
								"Defaults to a new line (\\n) but can be changed (e.g. comma, pipe).")
						.build()
		);
	}

	/**
	 * Output parser that splits LLM text output into a list of string items.
	 */
	static class ItemListOutputParser implements OutputParser {
		private final Integer numberOfItems;
		private final String separator;

		ItemListOutputParser(Integer numberOfItems, String separator) {
			this.numberOfItems = numberOfItems;
			this.separator = separator;
		}

		@Override
		public String getFormatInstructions() {
			String countText = numberOfItems != null ? numberOfItems + " " : "";
			int exampleCount = numberOfItems != null ? numberOfItems : 3;

			List<String> examples = new ArrayList<>();
			for (int i = 1; i <= exampleCount; i++) {
				examples.add("item" + i);
			}

			return "Your response should be a list of " + countText +
					"items separated by \"" + separator.replace("\n", "\\n") +
					"\" (for example: \"" + String.join(separator, examples).replace("\n", "\\n") + "\")";
		}

		@Override
		public Object parse(String text) throws OutputParserException {
			List<String> items = Arrays.stream(text.split(java.util.regex.Pattern.quote(separator)))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.collect(Collectors.toList());

			if (numberOfItems != null && items.size() < numberOfItems) {
				throw new OutputParserException(
						"Wrong number of items returned. Expected " + numberOfItems +
								" items but got " + items.size() + " items instead.",
						text
				);
			}

			if (numberOfItems != null) {
				return items.subList(0, Math.min(items.size(), numberOfItems));
			}
			return items;
		}
	}
}
