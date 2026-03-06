package io.cwc.nodes.impl;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * AWS SES — send emails, manage templates, and custom verification emails via Amazon SES.
 */
@Node(
		type = "awsSes",
		displayName = "AWS SES",
		description = "Send emails and manage templates with Amazon SES",
		category = "AWS",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsSesNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String resource = context.getParameter("resource", "email");
		String operation = context.getParameter("operation", "send");

		SesClient client = SesClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "email" -> handleEmail(context, client, operation);
					case "template" -> handleTemplate(context, client, operation);
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

		client.close();
		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleEmail(NodeExecutionContext context, SesClient client, String operation) {
		return switch (operation) {
			case "send" -> sendEmail(context, client);
			case "sendTemplate" -> sendTemplatedEmail(context, client);
			default -> throw new IllegalArgumentException("Unknown email operation: " + operation);
		};
	}

	private Map<String, Object> sendEmail(NodeExecutionContext context, SesClient client) {
		String fromEmail = context.getParameter("fromEmail", "");
		String toAddresses = context.getParameter("toAddresses", "");
		String subject = context.getParameter("subject", "");
		String body = context.getParameter("body", "");
		boolean isHtml = toBoolean(context.getParameters().get("isHtml"), true);
		String ccAddresses = context.getParameter("ccAddresses", "");
		String bccAddresses = context.getParameter("bccAddresses", "");
		String replyToAddresses = context.getParameter("replyToAddresses", "");

		Body emailBody;
		if (isHtml) {
			emailBody = Body.builder()
					.html(Content.builder().data(body).build())
					.build();
		} else {
			emailBody = Body.builder()
					.text(Content.builder().data(body).build())
					.build();
		}

		Destination.Builder destBuilder = Destination.builder()
				.toAddresses(Arrays.stream(toAddresses.split(",")).map(String::trim).toList());

		if (!ccAddresses.isBlank()) {
			destBuilder.ccAddresses(Arrays.stream(ccAddresses.split(",")).map(String::trim).toList());
		}
		if (!bccAddresses.isBlank()) {
			destBuilder.bccAddresses(Arrays.stream(bccAddresses.split(",")).map(String::trim).toList());
		}

		SendEmailRequest.Builder builder = SendEmailRequest.builder()
				.source(fromEmail)
				.destination(destBuilder.build())
				.message(Message.builder()
						.subject(Content.builder().data(subject).build())
						.body(emailBody)
						.build());

		if (!replyToAddresses.isBlank()) {
			builder.replyToAddresses(Arrays.stream(replyToAddresses.split(",")).map(String::trim).toList());
		}

		SendEmailResponse response = client.sendEmail(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("messageId", response.messageId());
		return result;
	}

	private Map<String, Object> sendTemplatedEmail(NodeExecutionContext context, SesClient client) {
		String fromEmail = context.getParameter("fromEmail", "");
		String toAddresses = context.getParameter("toAddresses", "");
		String templateName = context.getParameter("templateName", "");
		String templateData = context.getParameter("templateData", "{}");
		String ccAddresses = context.getParameter("ccAddresses", "");
		String bccAddresses = context.getParameter("bccAddresses", "");
		String replyToAddresses = context.getParameter("replyToAddresses", "");

		Destination.Builder destBuilder = Destination.builder()
				.toAddresses(Arrays.stream(toAddresses.split(",")).map(String::trim).toList());

		if (!ccAddresses.isBlank()) {
			destBuilder.ccAddresses(Arrays.stream(ccAddresses.split(",")).map(String::trim).toList());
		}
		if (!bccAddresses.isBlank()) {
			destBuilder.bccAddresses(Arrays.stream(bccAddresses.split(",")).map(String::trim).toList());
		}

		SendTemplatedEmailRequest.Builder builder = SendTemplatedEmailRequest.builder()
				.source(fromEmail)
				.destination(destBuilder.build())
				.template(templateName)
				.templateData(templateData);

		if (!replyToAddresses.isBlank()) {
			builder.replyToAddresses(Arrays.stream(replyToAddresses.split(",")).map(String::trim).toList());
		}

		SendTemplatedEmailResponse response = client.sendTemplatedEmail(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("messageId", response.messageId());
		return result;
	}

	private Map<String, Object> handleTemplate(NodeExecutionContext context, SesClient client, String operation) {
		return switch (operation) {
			case "create" -> createTemplate(context, client);
			case "delete" -> deleteTemplate(context, client);
			case "get" -> getTemplate(context, client);
			case "getMany" -> getManyTemplates(client);
			case "update" -> updateTemplate(context, client);
			default -> throw new IllegalArgumentException("Unknown template operation: " + operation);
		};
	}

	private Map<String, Object> createTemplate(NodeExecutionContext context, SesClient client) {
		String templateName = context.getParameter("templateName", "");
		String subjectPart = context.getParameter("subjectPart", "");
		String htmlPart = context.getParameter("htmlPart", "");
		String textPart = context.getParameter("textPart", "");

		Template template = Template.builder()
				.templateName(templateName)
				.subjectPart(subjectPart)
				.htmlPart(htmlPart)
				.textPart(textPart)
				.build();

		client.createTemplate(CreateTemplateRequest.builder().template(template).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("templateName", templateName);
		return result;
	}

	private Map<String, Object> deleteTemplate(NodeExecutionContext context, SesClient client) {
		String templateName = context.getParameter("templateName", "");
		client.deleteTemplate(DeleteTemplateRequest.builder().templateName(templateName).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("templateName", templateName);
		return result;
	}

	private Map<String, Object> getTemplate(NodeExecutionContext context, SesClient client) {
		String templateName = context.getParameter("templateName", "");
		GetTemplateResponse response = client.getTemplate(
				GetTemplateRequest.builder().templateName(templateName).build());

		Template t = response.template();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("templateName", t.templateName());
		result.put("subjectPart", t.subjectPart());
		result.put("htmlPart", t.htmlPart());
		result.put("textPart", t.textPart());
		return result;
	}

	private Map<String, Object> getManyTemplates(SesClient client) {
		ListTemplatesResponse response = client.listTemplates(ListTemplatesRequest.builder().build());

		List<Map<String, Object>> templates = new ArrayList<>();
		for (TemplateMetadata meta : response.templatesMetadata()) {
			Map<String, Object> t = new LinkedHashMap<>();
			t.put("name", meta.name());
			t.put("createdTimestamp", meta.createdTimestamp() != null ? meta.createdTimestamp().toString() : null);
			templates.add(t);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("templates", templates);
		return result;
	}

	private Map<String, Object> updateTemplate(NodeExecutionContext context, SesClient client) {
		String templateName = context.getParameter("templateName", "");
		String subjectPart = context.getParameter("subjectPart", "");
		String htmlPart = context.getParameter("htmlPart", "");
		String textPart = context.getParameter("textPart", "");

		Template template = Template.builder()
				.templateName(templateName)
				.subjectPart(subjectPart)
				.htmlPart(htmlPart)
				.textPart(textPart)
				.build();

		client.updateTemplate(UpdateTemplateRequest.builder().template(template).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("templateName", templateName);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("email")
						.options(List.of(
								ParameterOption.builder().name("Email").value("email").build(),
								ParameterOption.builder().name("Template").value("template").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("send")
						.options(List.of(
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Send Template").value("sendTemplate").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("fromEmail").displayName("From Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email address to send from (must be verified).").build(),
				NodeParameter.builder()
						.name("toAddresses").displayName("To Addresses")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of recipient email addresses.").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email subject line.").build(),
				NodeParameter.builder()
						.name("body").displayName("Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email body content.").build(),
				NodeParameter.builder()
						.name("isHtml").displayName("Is HTML")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Whether the body content is HTML.").build(),
				NodeParameter.builder()
						.name("templateName").displayName("Template Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the email template.").build(),
				NodeParameter.builder()
						.name("templateData").displayName("Template Data (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JSON data for template variables.").build(),
				NodeParameter.builder()
						.name("subjectPart").displayName("Subject Part")
						.type(ParameterType.STRING).defaultValue("")
						.description("Template subject part.").build(),
				NodeParameter.builder()
						.name("htmlPart").displayName("HTML Part")
						.type(ParameterType.STRING).defaultValue("")
						.description("Template HTML body part.").build(),
				NodeParameter.builder()
						.name("textPart").displayName("Text Part")
						.type(ParameterType.STRING).defaultValue("")
						.description("Template text body part.").build(),
				NodeParameter.builder()
						.name("ccAddresses").displayName("CC Addresses")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated CC email addresses.").build(),
				NodeParameter.builder()
						.name("bccAddresses").displayName("BCC Addresses")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated BCC email addresses.").build(),
				NodeParameter.builder()
						.name("replyToAddresses").displayName("Reply To")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated reply-to email addresses.").build()
		);
	}
}
