package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Keap — manage contacts, orders, and more in Keap (Infusionsoft).
 */
@Node(
		type = "keap",
		displayName = "Keap",
		description = "Manage contacts and orders in Keap (Infusionsoft)",
		category = "CRM & Sales",
		icon = "keap",
		credentials = {"keapOAuth2Api"}
)
public class KeapNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.infusionsoft.com/crm/rest/v1";

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
				.name("resource").displayName("Resource")
				.type(ParameterType.OPTIONS).required(true).defaultValue("contact")
				.options(List.of(
						ParameterOption.builder().name("Contact").value("contact").description("Manage contacts").build(),
						ParameterOption.builder().name("Contact Note").value("contactNote").description("Manage contact notes").build(),
						ParameterOption.builder().name("Contact Tag").value("contactTag").description("Manage contact tags").build(),
						ParameterOption.builder().name("E-Commerce Order").value("ecommerceOrder").description("Manage e-commerce orders").build(),
						ParameterOption.builder().name("E-Commerce Product").value("ecommerceProduct").description("Manage e-commerce products").build(),
						ParameterOption.builder().name("Email").value("email").description("Manage emails").build(),
						ParameterOption.builder().name("File").value("file").description("Manage files").build()
				)).build());

		// Contact operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contact"))))
				.options(List.of(
						ParameterOption.builder().name("Upsert").value("upsert").description("Create or update a contact").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Delete a contact").build(),
						ParameterOption.builder().name("Get").value("get").description("Get a contact").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all contacts").build()
				)).build());

		// Contact Note operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contactNote"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Delete").value("delete").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Update").value("update").build()
				)).build());

		// Contact Tag operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("contactTag"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").description("Apply a tag to a contact").build(),
						ParameterOption.builder().name("Delete").value("delete").description("Remove a tag from a contact").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all tags for a contact").build()
				)).build());

		// E-Commerce Order operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("ecommerceOrder"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Delete").value("delete").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build()
				)).build());

		// E-Commerce Product operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("ecommerceProduct"))))
				.options(List.of(
						ParameterOption.builder().name("Create").value("create").build(),
						ParameterOption.builder().name("Delete").value("delete").build(),
						ParameterOption.builder().name("Get").value("get").build(),
						ParameterOption.builder().name("Get All").value("getAll").build()
				)).build());

		// Email operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("email"))))
				.options(List.of(
						ParameterOption.builder().name("Create Record").value("createRecord").description("Create an email record").build(),
						ParameterOption.builder().name("Delete Record").value("deleteRecord").description("Delete an email record").build(),
						ParameterOption.builder().name("Get All").value("getAll").description("Get all emails").build(),
						ParameterOption.builder().name("Send").value("send").description("Send an email").build()
				)).build());

		// File operations
		params.add(NodeParameter.builder()
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
				.displayOptions(Map.of("show", Map.of("resource", List.of("file"))))
				.options(List.of(
						ParameterOption.builder().name("Delete").value("delete").build(),
						ParameterOption.builder().name("Get All").value("getAll").build(),
						ParameterOption.builder().name("Upload").value("upload").build()
				)).build());

		// Common parameters
		params.add(NodeParameter.builder()
				.name("contactId").displayName("Contact ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the contact.").build());

		params.add(NodeParameter.builder()
				.name("resourceId").displayName("ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the resource.").build());

		params.add(NodeParameter.builder()
				.name("tagId").displayName("Tag ID")
				.type(ParameterType.STRING).defaultValue("")
				.description("The ID of the tag.").build());

		params.add(NodeParameter.builder()
				.name("email").displayName("Email")
				.type(ParameterType.STRING).defaultValue("")
				.description("Email address.").build());

		params.add(NodeParameter.builder()
				.name("firstName").displayName("First Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("First name.").build());

		params.add(NodeParameter.builder()
				.name("lastName").displayName("Last Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Last name.").build());

		params.add(NodeParameter.builder()
				.name("title").displayName("Title / Subject")
				.type(ParameterType.STRING).defaultValue("")
				.description("Title or subject.").build());

		params.add(NodeParameter.builder()
				.name("body").displayName("Body")
				.type(ParameterType.STRING).defaultValue("")
				.description("Body content.").build());

		params.add(NodeParameter.builder()
				.name("productName").displayName("Product Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Name of the product.").build());

		params.add(NodeParameter.builder()
				.name("productPrice").displayName("Product Price")
				.type(ParameterType.NUMBER).defaultValue(0)
				.description("Price of the product.").build());

		params.add(NodeParameter.builder()
				.name("toAddress").displayName("To Address")
				.type(ParameterType.STRING).defaultValue("")
				.description("Recipient email address.").build());

		params.add(NodeParameter.builder()
				.name("fileName").displayName("File Name")
				.type(ParameterType.STRING).defaultValue("")
				.description("Name of the file.").build());

		params.add(NodeParameter.builder()
				.name("fileData").displayName("File Data (Base64)")
				.type(ParameterType.STRING).defaultValue("")
				.description("Base64-encoded file data.").build());

		params.add(NodeParameter.builder()
				.name("additionalFields").displayName("Additional Fields (JSON)")
				.type(ParameterType.JSON).defaultValue("{}")
				.description("Additional fields as JSON.").build());

		params.add(NodeParameter.builder()
				.name("returnAll").displayName("Return All")
				.type(ParameterType.BOOLEAN).defaultValue(false)
				.description("Whether to return all results.").build());

		params.add(NodeParameter.builder()
				.name("limit").displayName("Limit")
				.type(ParameterType.NUMBER).defaultValue(100)
				.description("Max number of results to return.").build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> executeContact(context, operation, headers);
					case "contactNote" -> executeContactNote(context, operation, headers);
					case "contactTag" -> executeContactTag(context, operation, headers);
					case "ecommerceOrder" -> executeOrder(context, operation, headers);
					case "ecommerceProduct" -> executeProduct(context, operation, headers);
					case "email" -> executeEmail(context, operation, headers);
					case "file" -> executeFile(context, operation, headers);
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

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> executeContact(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "upsert": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String email = context.getParameter("email", "");
				String firstName = context.getParameter("firstName", "");
				String lastName = context.getParameter("lastName", "");
				if (!email.isEmpty()) body.put("email_addresses", List.of(Map.of("email", email, "field", "EMAIL1")));
				if (!firstName.isEmpty()) body.put("given_name", firstName);
				if (!lastName.isEmpty()) body.put("family_name", lastName);
				body.put("duplicate_option", "Email");

				HttpResponse<String> response = put(BASE_URL + "/contacts", body, headers);
				return parseResponse(response);
			}
			case "delete": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(contactId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("contactId", contactId);
				return result;
			}
			case "get": {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(contactId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/contacts?limit=" + (returnAll ? 1000 : Math.min(limit, 1000));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown contact operation: " + operation);
		}
	}

	private Map<String, Object> executeContactNote(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String contactId = context.getParameter("contactId", "");

		switch (operation) {
			case "create": {
				String title = context.getParameter("title", "");
				String body = context.getParameter("body", "");
				Map<String, Object> noteBody = new LinkedHashMap<>();
				noteBody.put("contact_id", Long.parseLong(contactId));
				if (!title.isEmpty()) noteBody.put("title", title);
				if (!body.isEmpty()) noteBody.put("body", body);

				HttpResponse<String> response = post(BASE_URL + "/notes", noteBody, headers);
				return parseResponse(response);
			}
			case "delete": {
				String noteId = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/notes/" + encode(noteId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("noteId", noteId);
				return result;
			}
			case "get": {
				String noteId = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/notes/" + encode(noteId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/contacts/" + encode(contactId) + "/notes?limit=" + (returnAll ? 1000 : Math.min(limit, 1000));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "update": {
				String noteId = context.getParameter("resourceId", "");
				String title = context.getParameter("title", "");
				String body = context.getParameter("body", "");
				Map<String, Object> noteBody = new LinkedHashMap<>();
				if (!title.isEmpty()) noteBody.put("title", title);
				if (!body.isEmpty()) noteBody.put("body", body);

				HttpResponse<String> response = patch(BASE_URL + "/notes/" + encode(noteId), noteBody, headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown contact note operation: " + operation);
		}
	}

	private Map<String, Object> executeContactTag(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String contactId = context.getParameter("contactId", "");

		switch (operation) {
			case "create": {
				String tagId = context.getParameter("tagId", "");
				Map<String, Object> body = Map.of("tagIds", List.of(Long.parseLong(tagId)));
				HttpResponse<String> response = post(BASE_URL + "/contacts/" + encode(contactId) + "/tags", body, headers);
				return parseResponse(response);
			}
			case "delete": {
				String tagId = context.getParameter("tagId", "");
				HttpResponse<String> response = delete(BASE_URL + "/contacts/" + encode(contactId) + "/tags/" + encode(tagId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("tagId", tagId);
				return result;
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/contacts/" + encode(contactId) + "/tags", headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown contact tag operation: " + operation);
		}
	}

	private Map<String, Object> executeOrder(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String contactId = context.getParameter("contactId", "");
				String title = context.getParameter("title", "");
				if (!contactId.isEmpty()) body.put("contact_id", Long.parseLong(contactId));
				if (!title.isEmpty()) body.put("title", title);

				HttpResponse<String> response = post(BASE_URL + "/orders", body, headers);
				return parseResponse(response);
			}
			case "delete": {
				String orderId = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/orders/" + encode(orderId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("orderId", orderId);
				return result;
			}
			case "get": {
				String orderId = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/orders/" + encode(orderId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/orders?limit=" + (returnAll ? 1000 : Math.min(limit, 1000));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown order operation: " + operation);
		}
	}

	private Map<String, Object> executeProduct(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String productName = context.getParameter("productName", "");
				double price = toDouble(context.getParameters().get("productPrice"), 0);
				if (!productName.isEmpty()) body.put("product_name", productName);
				if (price > 0) body.put("product_price", price);

				HttpResponse<String> response = post(BASE_URL + "/products", body, headers);
				return parseResponse(response);
			}
			case "delete": {
				String productId = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/products/" + encode(productId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("productId", productId);
				return result;
			}
			case "get": {
				String productId = context.getParameter("resourceId", "");
				HttpResponse<String> response = get(BASE_URL + "/products/" + encode(productId), headers);
				return parseResponse(response);
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/products?limit=" + (returnAll ? 1000 : Math.min(limit, 1000));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown product operation: " + operation);
		}
	}

	private Map<String, Object> executeEmail(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "createRecord": {
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				String contactId = context.getParameter("contactId", "");
				String title = context.getParameter("title", "");
				String emailBody = context.getParameter("body", "");
				if (!contactId.isEmpty()) body.put("contact_id", Long.parseLong(contactId));
				if (!title.isEmpty()) body.put("subject", title);
				if (!emailBody.isEmpty()) body.put("body", emailBody);

				HttpResponse<String> response = post(BASE_URL + "/emails", body, headers);
				return parseResponse(response);
			}
			case "deleteRecord": {
				String emailId = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/emails/" + encode(emailId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("emailId", emailId);
				return result;
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/emails?limit=" + (returnAll ? 1000 : Math.min(limit, 1000));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "send": {
				String toAddress = context.getParameter("toAddress", "");
				String title = context.getParameter("title", "");
				String emailBody = context.getParameter("body", "");
				String contactId = context.getParameter("contactId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("subject", title);
				body.put("body", emailBody);
				if (!contactId.isEmpty()) body.put("contacts", List.of(Long.parseLong(contactId)));
				if (!toAddress.isEmpty()) {
					body.put("address", toAddress);
				}

				HttpResponse<String> response = post(BASE_URL + "/emails/queue", body, headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown email operation: " + operation);
		}
	}

	private Map<String, Object> executeFile(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "delete": {
				String fileId = context.getParameter("resourceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/files/" + encode(fileId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("fileId", fileId);
				return result;
			}
			case "getAll": {
				boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
				int limit = toInt(context.getParameters().get("limit"), 100);
				String url = BASE_URL + "/files?limit=" + (returnAll ? 1000 : Math.min(limit, 1000));
				HttpResponse<String> response = get(url, headers);
				return parseResponse(response);
			}
			case "upload": {
				String fileName = context.getParameter("fileName", "");
				String fileData = context.getParameter("fileData", "");
				String contactId = context.getParameter("contactId", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("file_name", fileName);
				body.put("file_data", fileData);
				if (!contactId.isEmpty()) body.put("contact_id", Long.parseLong(contactId));

				HttpResponse<String> response = post(BASE_URL + "/files", body, headers);
				return parseResponse(response);
			}
			default:
				throw new IllegalArgumentException("Unknown file operation: " + operation);
		}
	}
}
