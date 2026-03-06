package io.cwc.nodes.core;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * Wraps a programmatically-defined tool with a dynamic name and description.
 * Used instead of @Tool annotations when the tool name/description must be
 * configurable at runtime.
 */
public record DynamicTool(ToolSpecification specification, ToolExecutor executor) {
}
