package io.trellis.nodes.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;

public abstract class AbstractTriggerNode extends AbstractNode {
	
	@Override
	public List<NodeInput> getInputs() {
		// trigger node does not have any inputs
		return List.of();
	}
	
	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder()
				.name("main")
				.displayName("Main Output")
				.build());
	}
	
	// create trigger output with timestamp
	protected Map<String, Object> createTriggerItem(Map<String, Object> data) {
		Map<String, Object> item = new HashMap<>(data);
		item.put("_triggerTimestamp", System.currentTimeMillis());
		return wrapInJson(item);
	}
	
	// create an empty trigger item
	protected Map<String, Object> createEmptyTriggerItem() {
		return wrapInJson(Map.of("_triggerTimestamp", System.currentTimeMillis()));
	}

}
