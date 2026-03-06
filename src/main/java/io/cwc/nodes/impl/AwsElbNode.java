package io.cwc.nodes.impl;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * AWS ELB — manage Elastic Load Balancers (Application and Network) via AWS ELBv2.
 */
@Node(
		type = "awsElb",
		displayName = "AWS ELB",
		description = "Manage Elastic Load Balancers",
		category = "AWS",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsElbNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String resource = context.getParameter("resource", "loadBalancer");
		String operation = context.getParameter("operation", "getMany");

		ElasticLoadBalancingV2Client client = ElasticLoadBalancingV2Client.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "loadBalancer" -> handleLoadBalancer(context, client, operation);
					case "listenerCertificate" -> handleListenerCertificate(context, client, operation);
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

	private Map<String, Object> handleLoadBalancer(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client, String operation) {
		return switch (operation) {
			case "create" -> createLoadBalancer(context, client);
			case "delete" -> deleteLoadBalancer(context, client);
			case "get" -> getLoadBalancer(context, client);
			case "getMany" -> getManyLoadBalancers(context, client);
			default -> throw new IllegalArgumentException("Unknown LB operation: " + operation);
		};
	}

	private Map<String, Object> createLoadBalancer(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client) {
		String name = context.getParameter("name", "");
		String scheme = context.getParameter("scheme", "internet-facing");
		String type = context.getParameter("type", "application");
		String ipAddressType = context.getParameter("ipAddressType", "ipv4");
		String subnets = context.getParameter("subnets", "");
		String securityGroups = context.getParameter("securityGroups", "");

		CreateLoadBalancerRequest.Builder builder = CreateLoadBalancerRequest.builder()
				.name(name)
				.scheme(scheme)
				.type(type)
				.ipAddressType(ipAddressType);

		if (!subnets.isBlank()) {
			builder.subnets(Arrays.stream(subnets.split(",")).map(String::trim).toList());
		}
		if (!securityGroups.isBlank()) {
			builder.securityGroups(Arrays.stream(securityGroups.split(",")).map(String::trim).toList());
		}

		CreateLoadBalancerResponse response = client.createLoadBalancer(builder.build());

		LoadBalancer lb = response.loadBalancers().get(0);
		return loadBalancerToMap(lb);
	}

	private Map<String, Object> deleteLoadBalancer(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client) {
		String loadBalancerArn = context.getParameter("loadBalancerArn", "");
		client.deleteLoadBalancer(DeleteLoadBalancerRequest.builder()
				.loadBalancerArn(loadBalancerArn).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("loadBalancerArn", loadBalancerArn);
		return result;
	}

	private Map<String, Object> getLoadBalancer(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client) {
		String loadBalancerArn = context.getParameter("loadBalancerArn", "");
		DescribeLoadBalancersResponse response = client.describeLoadBalancers(
				DescribeLoadBalancersRequest.builder()
						.loadBalancerArns(loadBalancerArn).build());

		if (response.loadBalancers().isEmpty()) {
			throw new IllegalStateException("Load balancer not found: " + loadBalancerArn);
		}

		return loadBalancerToMap(response.loadBalancers().get(0));
	}

	private Map<String, Object> getManyLoadBalancers(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		String names = context.getParameter("names", "");

		DescribeLoadBalancersRequest.Builder builder = DescribeLoadBalancersRequest.builder();
		if (!names.isBlank()) {
			builder.names(Arrays.stream(names.split(",")).map(String::trim).toList());
		}

		List<Map<String, Object>> loadBalancers = new ArrayList<>();
		String marker = null;
		do {
			if (marker != null) {
				builder.marker(marker);
			}
			DescribeLoadBalancersResponse response = client.describeLoadBalancers(builder.build());
			for (LoadBalancer lb : response.loadBalancers()) {
				loadBalancers.add(loadBalancerToMap(lb));
				if (!returnAll && loadBalancers.size() >= limit) break;
			}
			marker = response.nextMarker();
		} while (returnAll && marker != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("loadBalancers", loadBalancers);
		return result;
	}

	private Map<String, Object> loadBalancerToMap(LoadBalancer lb) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("loadBalancerArn", lb.loadBalancerArn());
		result.put("loadBalancerName", lb.loadBalancerName());
		result.put("dnsName", lb.dnsName());
		result.put("scheme", lb.schemeAsString());
		result.put("type", lb.typeAsString());
		result.put("state", lb.state() != null ? lb.state().codeAsString() : null);
		result.put("vpcId", lb.vpcId());
		result.put("ipAddressType", lb.ipAddressTypeAsString());
		result.put("createdTime", lb.createdTime() != null ? lb.createdTime().toString() : null);
		return result;
	}

	private Map<String, Object> handleListenerCertificate(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client, String operation) {
		return switch (operation) {
			case "add" -> addListenerCertificate(context, client);
			case "getMany" -> getManyListenerCertificates(context, client);
			case "remove" -> removeListenerCertificate(context, client);
			default -> throw new IllegalArgumentException("Unknown certificate operation: " + operation);
		};
	}

	private Map<String, Object> addListenerCertificate(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client) {
		String listenerArn = context.getParameter("listenerArn", "");
		String certificateArn = context.getParameter("certificateArn", "");

		client.addListenerCertificates(AddListenerCertificatesRequest.builder()
				.listenerArn(listenerArn)
				.certificates(Certificate.builder().certificateArn(certificateArn).build())
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("listenerArn", listenerArn);
		result.put("certificateArn", certificateArn);
		return result;
	}

	private Map<String, Object> getManyListenerCertificates(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client) {
		String listenerArn = context.getParameter("listenerArn", "");

		DescribeListenerCertificatesResponse response = client.describeListenerCertificates(
				DescribeListenerCertificatesRequest.builder()
						.listenerArn(listenerArn).build());

		List<Map<String, Object>> certs = new ArrayList<>();
		for (Certificate cert : response.certificates()) {
			Map<String, Object> c = new LinkedHashMap<>();
			c.put("certificateArn", cert.certificateArn());
			c.put("isDefault", cert.isDefault());
			certs.add(c);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("certificates", certs);
		return result;
	}

	private Map<String, Object> removeListenerCertificate(NodeExecutionContext context,
			ElasticLoadBalancingV2Client client) {
		String listenerArn = context.getParameter("listenerArn", "");
		String certificateArn = context.getParameter("certificateArn", "");

		client.removeListenerCertificates(RemoveListenerCertificatesRequest.builder()
				.listenerArn(listenerArn)
				.certificates(Certificate.builder().certificateArn(certificateArn).build())
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("listenerArn", listenerArn);
		result.put("certificateArn", certificateArn);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("loadBalancer")
						.options(List.of(
								ParameterOption.builder().name("Load Balancer").value("loadBalancer").build(),
								ParameterOption.builder().name("Listener Certificate").value("listenerCertificate").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("getMany")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Remove").value("remove").build()
						)).build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for the new load balancer (max 32 chars).").build(),
				NodeParameter.builder()
						.name("scheme").displayName("Scheme")
						.type(ParameterType.OPTIONS)
						.defaultValue("internet-facing")
						.options(List.of(
								ParameterOption.builder().name("Internet Facing").value("internet-facing").build(),
								ParameterOption.builder().name("Internal").value("internal").build()
						)).build(),
				NodeParameter.builder()
						.name("type").displayName("Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("application")
						.options(List.of(
								ParameterOption.builder().name("Application").value("application").build(),
								ParameterOption.builder().name("Network").value("network").build()
						)).build(),
				NodeParameter.builder()
						.name("ipAddressType").displayName("IP Address Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("ipv4")
						.options(List.of(
								ParameterOption.builder().name("IPv4").value("ipv4").build(),
								ParameterOption.builder().name("Dual Stack").value("dualstack").build()
						)).build(),
				NodeParameter.builder()
						.name("subnets").displayName("Subnets")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated subnet IDs.").build(),
				NodeParameter.builder()
						.name("securityGroups").displayName("Security Groups")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated security group IDs.").build(),
				NodeParameter.builder()
						.name("loadBalancerArn").displayName("Load Balancer ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ARN of the load balancer.").build(),
				NodeParameter.builder()
						.name("listenerArn").displayName("Listener ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ARN of the listener.").build(),
				NodeParameter.builder()
						.name("certificateArn").displayName("Certificate ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ARN of the certificate.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of results to return.").build(),
				NodeParameter.builder()
						.name("names").displayName("Names")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter by load balancer names (comma-separated).").build()
		);
	}
}
