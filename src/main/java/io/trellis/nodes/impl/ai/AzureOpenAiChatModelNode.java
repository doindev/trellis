package io.trellis.nodes.impl.ai;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractChatModelNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;

@Node(
		type = "azureOpenAiChatModel",
		displayName = "Azure OpenAI Chat Model",
		description = "Azure-hosted OpenAI chat model",
		category = "AI / Chat Models",
		icon = "azure",
		credentials = {"azureOpenAiApi"},
		searchOnly = true
)
public class AzureOpenAiChatModelNode extends AbstractChatModelNode {

	@Override
	protected ChatModel createChatModel(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		String endpoint = context.getCredentialString("endpoint");
		String apiVersion = context.getCredentialString("apiVersion", "2024-02-15-preview");
		String deploymentName = context.getParameter("deploymentName", "");
		double temperature = toDouble(context.getParameters().get("temperature"), 0.7);
		int maxTokens = toInt(context.getParameters().get("maxTokens"), 0);

		var builder = AzureOpenAiChatModel.builder()
				.apiKey(apiKey)
				.endpoint(endpoint)
				.serviceVersion(apiVersion)
				.deploymentName(deploymentName)
				.temperature(temperature);

		if (maxTokens > 0) {
			builder.maxTokens(maxTokens);
		}

		return builder.build();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("deploymentName").displayName("Deployment Name")
						.type(ParameterType.STRING)
						.required(true)
						.description("The name of your Azure OpenAI deployment")
						.build(),
				NodeParameter.builder()
						.name("temperature").displayName("Temperature")
						.type(ParameterType.NUMBER)
						.defaultValue(0.7)
						.build(),
				NodeParameter.builder()
						.name("maxTokens").displayName("Max Tokens")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Maximum number of tokens to generate. 0 for model default.")
						.build()
		);
	}
}
