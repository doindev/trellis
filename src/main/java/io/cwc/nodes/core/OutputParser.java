package io.cwc.nodes.core;

/**
 * Interface for output parsers that AI sub-nodes supply to parent chain/agent nodes.
 * Output parsers provide format instructions for the LLM and parse the LLM's text response
 * into structured data (maps, lists, etc.).
 */
public interface OutputParser {

	/**
	 * Returns instructions telling the LLM how to format its output
	 * so this parser can successfully parse it.
	 */
	String getFormatInstructions();

	/**
	 * Parse the LLM's text output into structured data.
	 *
	 * @param text the raw text output from the LLM
	 * @return parsed result — typically a {@code Map<String, Object>} or {@code List<String>}
	 * @throws OutputParserException if the text cannot be parsed
	 */
	Object parse(String text) throws OutputParserException;

	/**
	 * Exception thrown when output parsing fails.
	 */
	class OutputParserException extends Exception {
		private static final long serialVersionUID = 1L;
		private final String completion;

		public OutputParserException(String message) {
			this(message, null);
		}

		public OutputParserException(String message, String completion) {
			super(message);
			this.completion = completion;
		}

		/** The original LLM completion that failed to parse. */
		public String getCompletion() {
			return completion;
		}
	}
}
