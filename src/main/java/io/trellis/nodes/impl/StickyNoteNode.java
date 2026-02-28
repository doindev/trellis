package io.trellis.nodes.impl;

import java.util.List;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

/**
 * Sticky Note Node - a visual annotation node with no data flow.
 * Used purely for workflow documentation and organization.
 */
@Node(
	type = "stickyNote",
	displayName = "Sticky Note",
	description = "Add a visual note to your workflow for documentation. Does not process any data.",
	category = "Miscellaneous",
	icon = "sticky-note",
	group = "annotation"
)
public class StickyNoteNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("content")
				.displayName("Content")
				.description("The note content (supports markdown).")
				.type(ParameterType.STRING)
				.defaultValue("")
				.typeOptions(java.util.Map.of("rows", 6))
				.placeHolder("Write your notes here...")
				.build(),

			NodeParameter.builder()
				.name("color")
				.displayName("Color")
				.description("The background color of the sticky note.")
				.type(ParameterType.OPTIONS)
				.defaultValue("yellow")
				.options(List.of(
					ParameterOption.builder().name("Yellow").value("yellow").build(),
					ParameterOption.builder().name("Blue").value("blue").build(),
					ParameterOption.builder().name("Green").value("green").build(),
					ParameterOption.builder().name("Pink").value("pink").build(),
					ParameterOption.builder().name("Purple").value("purple").build()
				))
				.build(),

			NodeParameter.builder()
				.name("width")
				.displayName("Width")
				.description("The width of the sticky note in pixels.")
				.type(ParameterType.NUMBER)
				.defaultValue(200)
				.minValue(100)
				.maxValue(600)
				.isNodeSetting(true)
				.build(),

			NodeParameter.builder()
				.name("height")
				.displayName("Height")
				.description("The height of the sticky note in pixels.")
				.type(ParameterType.NUMBER)
				.defaultValue(150)
				.minValue(80)
				.maxValue(600)
				.isNodeSetting(true)
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		// Sticky notes are purely visual — they don't process data
		return NodeExecutionResult.empty();
	}
}
