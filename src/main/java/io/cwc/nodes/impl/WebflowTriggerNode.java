package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Webflow Trigger — webhook trigger node that receives Webflow events such as
 * form submissions, site publishes, e-commerce orders, and collection item changes.
 */
@Node(
		type = "webflowTrigger",
		displayName = "Webflow Trigger",
		description = "Trigger workflows on Webflow events",
		category = "CMS / Website Builders",
		icon = "webflow",
		trigger = true,
		credentials = {"webflowOAuth2Api"}
)
public class WebflowTriggerNode extends AbstractTriggerNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.success(List.of(createEmptyTriggerItem()));
		}

		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> data = unwrapJson(item);
			results.add(createTriggerItem(data));
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("siteId").displayName("Site ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("The ID of the Webflow site to listen for events on.").build(),
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("form_submission")
						.required(true)
						.options(List.of(
								ParameterOption.builder()
										.name("Form Submission")
										.value("form_submission")
										.description("Triggers when a form is submitted.").build(),
								ParameterOption.builder()
										.name("Site Publish")
										.value("site_publish")
										.description("Triggers when the site is published.").build(),
								ParameterOption.builder()
										.name("Page Created")
										.value("page_created")
										.description("Triggers when a new page is created.").build(),
								ParameterOption.builder()
										.name("Page Metadata Updated")
										.value("page_metadata_updated")
										.description("Triggers when page metadata is updated.").build(),
								ParameterOption.builder()
										.name("Page Deleted")
										.value("page_deleted")
										.description("Triggers when a page is deleted.").build(),
								ParameterOption.builder()
										.name("E-commerce New Order")
										.value("ecomm_new_order")
										.description("Triggers when a new e-commerce order is placed.").build(),
								ParameterOption.builder()
										.name("E-commerce Order Changed")
										.value("ecomm_order_changed")
										.description("Triggers when an e-commerce order is changed.").build(),
								ParameterOption.builder()
										.name("E-commerce Inventory Changed")
										.value("ecomm_inventory_changed")
										.description("Triggers when e-commerce inventory changes.").build(),
								ParameterOption.builder()
										.name("Collection Item Created")
										.value("collection_item_created")
										.description("Triggers when a collection item is created.").build(),
								ParameterOption.builder()
										.name("Collection Item Changed")
										.value("collection_item_changed")
										.description("Triggers when a collection item is changed.").build(),
								ParameterOption.builder()
										.name("Collection Item Deleted")
										.value("collection_item_deleted")
										.description("Triggers when a collection item is deleted.").build(),
								ParameterOption.builder()
										.name("Collection Item Unpublished")
										.value("collection_item_unpublished")
										.description("Triggers when a collection item is unpublished.").build()
						)).build()
		);
	}
}
