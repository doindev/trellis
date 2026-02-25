package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.*;

import java.util.*;

/**
 * AWS Certificate Manager — manage SSL/TLS certificates via AWS ACM.
 */
@Node(
		type = "awsCertificateManager",
		displayName = "AWS Certificate Manager",
		description = "Manage SSL/TLS certificates with AWS ACM",
		category = "AWS Services",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsCertificateManagerNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String operation = context.getParameter("operation", "get");

		AcmClient client = AcmClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "delete" -> handleDelete(context, client);
					case "get" -> handleGet(context, client);
					case "getMany" -> handleGetMany(context, client);
					case "getMetadata" -> handleGetMetadata(context, client);
					case "renew" -> handleRenew(context, client);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	private Map<String, Object> handleDelete(NodeExecutionContext context, AcmClient client) {
		String certificateArn = context.getParameter("certificateArn", "");
		client.deleteCertificate(DeleteCertificateRequest.builder()
				.certificateArn(certificateArn).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("certificateArn", certificateArn);
		return result;
	}

	private Map<String, Object> handleGet(NodeExecutionContext context, AcmClient client) {
		String certificateArn = context.getParameter("certificateArn", "");
		GetCertificateResponse response = client.getCertificate(
				GetCertificateRequest.builder().certificateArn(certificateArn).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("certificate", response.certificate());
		result.put("certificateChain", response.certificateChain());
		return result;
	}

	private Map<String, Object> handleGetMany(NodeExecutionContext context, AcmClient client) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);

		ListCertificatesRequest.Builder builder = ListCertificatesRequest.builder();
		if (!returnAll) {
			builder.maxItems(limit);
		}

		List<Map<String, Object>> certificates = new ArrayList<>();
		String nextToken = null;
		do {
			if (nextToken != null) {
				builder.nextToken(nextToken);
			}
			ListCertificatesResponse response = client.listCertificates(builder.build());
			for (CertificateSummary cert : response.certificateSummaryList()) {
				Map<String, Object> c = new LinkedHashMap<>();
				c.put("certificateArn", cert.certificateArn());
				c.put("domainName", cert.domainName());
				c.put("status", cert.statusAsString());
				c.put("type", cert.typeAsString());
				certificates.add(c);
				if (!returnAll && certificates.size() >= limit) break;
			}
			nextToken = response.nextToken();
		} while (returnAll && nextToken != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("certificates", certificates);
		return result;
	}

	private Map<String, Object> handleGetMetadata(NodeExecutionContext context, AcmClient client) {
		String certificateArn = context.getParameter("certificateArn", "");
		DescribeCertificateResponse response = client.describeCertificate(
				DescribeCertificateRequest.builder().certificateArn(certificateArn).build());

		CertificateDetail detail = response.certificate();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("certificateArn", detail.certificateArn());
		result.put("domainName", detail.domainName());
		result.put("status", detail.statusAsString());
		result.put("type", detail.typeAsString());
		result.put("issuer", detail.issuer());
		result.put("createdAt", detail.createdAt() != null ? detail.createdAt().toString() : null);
		result.put("issuedAt", detail.issuedAt() != null ? detail.issuedAt().toString() : null);
		result.put("notBefore", detail.notBefore() != null ? detail.notBefore().toString() : null);
		result.put("notAfter", detail.notAfter() != null ? detail.notAfter().toString() : null);
		result.put("keyAlgorithm", detail.keyAlgorithmAsString());
		result.put("serial", detail.serial());

		List<String> sans = detail.subjectAlternativeNames();
		if (sans != null) {
			result.put("subjectAlternativeNames", sans);
		}
		return result;
	}

	private Map<String, Object> handleRenew(NodeExecutionContext context, AcmClient client) {
		String certificateArn = context.getParameter("certificateArn", "");
		client.renewCertificate(RenewCertificateRequest.builder()
				.certificateArn(certificateArn).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("certificateArn", certificateArn);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Get Metadata").value("getMetadata").build(),
								ParameterOption.builder().name("Renew").value("renew").build()
						)).build(),
				NodeParameter.builder()
						.name("certificateArn").displayName("Certificate ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ARN of the ACM certificate.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of results to return.").build()
		);
	}
}
