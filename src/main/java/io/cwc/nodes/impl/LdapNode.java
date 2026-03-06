package io.cwc.nodes.impl;

import java.util.*;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * LDAP Node -- perform LDAP operations (compare, create, delete, modify,
 * rename, search) using Java JNDI.
 */
@Slf4j
@Node(
	type = "ldap",
	displayName = "LDAP",
	description = "Perform LDAP operations: compare, create, delete, modify, rename, search",
	category = "Miscellaneous",
	icon = "ldap",
	credentials = {"ldapApi"}
)
public class LdapNode extends AbstractNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("search")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Compare").value("compare").description("Compare an attribute value").build(),
				ParameterOption.builder().name("Create").value("create").description("Create a new entry").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an entry").build(),
				ParameterOption.builder().name("Modify").value("modify").description("Modify an entry").build(),
				ParameterOption.builder().name("Rename").value("rename").description("Rename an entry").build(),
				ParameterOption.builder().name("Search").value("search").description("Search for entries").build()
			)).build());

		// DN parameter for all operations
		params.add(NodeParameter.builder()
			.name("dn").displayName("DN").type(ParameterType.STRING).required(true)
			.description("Distinguished Name of the entry")
			.build());

		// Compare parameters
		params.add(NodeParameter.builder()
			.name("compareAttribute").displayName("Attribute Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("operation", List.of("compare"))))
			.build());
		params.add(NodeParameter.builder()
			.name("compareValue").displayName("Attribute Value").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("operation", List.of("compare"))))
			.build());

		// Create parameters
		params.add(NodeParameter.builder()
			.name("createAttributes").displayName("Attributes (JSON)")
			.type(ParameterType.STRING).required(true)
			.description("JSON object of attributes, e.g. {\"cn\":\"John\",\"sn\":\"Doe\",\"objectClass\":[\"inetOrgPerson\",\"person\"]}")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create"))))
			.build());

		// Modify parameters
		params.add(NodeParameter.builder()
			.name("modifyOperation").displayName("Modification Type").type(ParameterType.OPTIONS).defaultValue("replace")
			.displayOptions(Map.of("show", Map.of("operation", List.of("modify"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").build(),
				ParameterOption.builder().name("Replace").value("replace").build(),
				ParameterOption.builder().name("Remove").value("remove").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("modifyAttributes").displayName("Attributes (JSON)")
			.type(ParameterType.STRING).required(true)
			.description("JSON object of attributes to modify, e.g. {\"mail\":\"new@example.com\"}")
			.displayOptions(Map.of("show", Map.of("operation", List.of("modify"))))
			.build());

		// Rename parameters
		params.add(NodeParameter.builder()
			.name("newRdn").displayName("New RDN").type(ParameterType.STRING).required(true)
			.description("The new relative distinguished name, e.g. cn=NewName")
			.displayOptions(Map.of("show", Map.of("operation", List.of("rename"))))
			.build());

		// Search parameters
		params.add(NodeParameter.builder()
			.name("searchFilter").displayName("Filter").type(ParameterType.STRING).defaultValue("(objectClass=*)")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());
		params.add(NodeParameter.builder()
			.name("searchScope").displayName("Scope").type(ParameterType.OPTIONS).defaultValue("subtree")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.options(List.of(
				ParameterOption.builder().name("Base").value("base").build(),
				ParameterOption.builder().name("One Level").value("onelevel").build(),
				ParameterOption.builder().name("Subtree").value("subtree").build()
			)).build());
		params.add(NodeParameter.builder()
			.name("searchAttributes").displayName("Attributes").type(ParameterType.STRING)
			.description("Comma-separated list of attributes to return (empty for all)")
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());
		params.add(NodeParameter.builder()
			.name("searchLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(100)
			.displayOptions(Map.of("show", Map.of("operation", List.of("search"))))
			.build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "search");
		Map<String, Object> credentials = context.getCredentials();

		DirContext dirContext = null;
		try {
			dirContext = createDirContext(credentials);

			return switch (operation) {
				case "compare" -> executeCompare(context, dirContext);
				case "create" -> executeCreate(context, dirContext);
				case "delete" -> executeDelete(context, dirContext);
				case "modify" -> executeModify(context, dirContext);
				case "rename" -> executeRename(context, dirContext);
				case "search" -> executeSearch(context, dirContext);
				default -> NodeExecutionResult.error("Unknown LDAP operation: " + operation);
			};
		} catch (Exception e) {
			return handleError(context, "LDAP operation error: " + e.getMessage(), e);
		} finally {
			if (dirContext != null) {
				try { dirContext.close(); } catch (Exception ignored) {}
			}
		}
	}

	private NodeExecutionResult executeCompare(NodeExecutionContext context, DirContext dirContext) throws Exception {
		String dn = context.getParameter("dn", "");
		String attrName = context.getParameter("compareAttribute", "");
		String attrValue = context.getParameter("compareValue", "");

		Attributes attrs = dirContext.getAttributes(dn, new String[]{attrName});
		Attribute attr = attrs.get(attrName);

		boolean match = false;
		if (attr != null) {
			NamingEnumeration<?> values = attr.getAll();
			while (values.hasMore()) {
				if (attrValue.equals(String.valueOf(values.next()))) {
					match = true;
					break;
				}
			}
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("dn", dn, "attribute", attrName, "match", match))));
	}

	private NodeExecutionResult executeCreate(NodeExecutionContext context, DirContext dirContext) throws Exception {
		String dn = context.getParameter("dn", "");
		String attrsJson = context.getParameter("createAttributes", "{}");

		Map<String, Object> attrMap = parseJsonAttributes(attrsJson);
		BasicAttributes basicAttrs = new BasicAttributes(true);

		for (Map.Entry<String, Object> entry : attrMap.entrySet()) {
			BasicAttribute attr = new BasicAttribute(entry.getKey());
			if (entry.getValue() instanceof List) {
				for (Object val : (List<?>) entry.getValue()) {
					attr.add(String.valueOf(val));
				}
			} else {
				attr.add(String.valueOf(entry.getValue()));
			}
			basicAttrs.put(attr);
		}

		dirContext.createSubcontext(dn, basicAttrs);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("dn", dn, "created", true))));
	}

	private NodeExecutionResult executeDelete(NodeExecutionContext context, DirContext dirContext) throws Exception {
		String dn = context.getParameter("dn", "");
		dirContext.destroySubcontext(dn);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("dn", dn, "deleted", true))));
	}

	private NodeExecutionResult executeModify(NodeExecutionContext context, DirContext dirContext) throws Exception {
		String dn = context.getParameter("dn", "");
		String modOp = context.getParameter("modifyOperation", "replace");
		String attrsJson = context.getParameter("modifyAttributes", "{}");

		int modType = switch (modOp) {
			case "add" -> DirContext.ADD_ATTRIBUTE;
			case "remove" -> DirContext.REMOVE_ATTRIBUTE;
			default -> DirContext.REPLACE_ATTRIBUTE;
		};

		Map<String, Object> attrMap = parseJsonAttributes(attrsJson);
		List<ModificationItem> mods = new ArrayList<>();

		for (Map.Entry<String, Object> entry : attrMap.entrySet()) {
			BasicAttribute attr = new BasicAttribute(entry.getKey());
			if (entry.getValue() instanceof List) {
				for (Object val : (List<?>) entry.getValue()) {
					attr.add(String.valueOf(val));
				}
			} else {
				attr.add(String.valueOf(entry.getValue()));
			}
			mods.add(new ModificationItem(modType, attr));
		}

		dirContext.modifyAttributes(dn, mods.toArray(new ModificationItem[0]));
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("dn", dn, "modified", true, "operation", modOp))));
	}

	private NodeExecutionResult executeRename(NodeExecutionContext context, DirContext dirContext) throws Exception {
		String dn = context.getParameter("dn", "");
		String newRdn = context.getParameter("newRdn", "");

		// Build new DN: new RDN + parent of old DN
		String parentDn = "";
		int commaIndex = dn.indexOf(',');
		if (commaIndex > 0) {
			parentDn = dn.substring(commaIndex + 1);
		}
		String newDn = newRdn + (parentDn.isEmpty() ? "" : "," + parentDn);

		dirContext.rename(dn, newDn);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("oldDn", dn, "newDn", newDn, "renamed", true))));
	}

	private NodeExecutionResult executeSearch(NodeExecutionContext context, DirContext dirContext) throws Exception {
		String dn = context.getParameter("dn", "");
		String filter = context.getParameter("searchFilter", "(objectClass=*)");
		String scope = context.getParameter("searchScope", "subtree");
		String attrsStr = context.getParameter("searchAttributes", "");
		int limit = toInt(context.getParameter("searchLimit", 100), 100);

		SearchControls controls = new SearchControls();
		controls.setSearchScope(switch (scope) {
			case "base" -> SearchControls.OBJECT_SCOPE;
			case "onelevel" -> SearchControls.ONELEVEL_SCOPE;
			default -> SearchControls.SUBTREE_SCOPE;
		});
		controls.setCountLimit(limit);

		if (!attrsStr.isEmpty()) {
			controls.setReturningAttributes(attrsStr.split(","));
		}

		NamingEnumeration<SearchResult> results = dirContext.search(dn, filter, controls);
		List<Map<String, Object>> items = new ArrayList<>();

		while (results.hasMore()) {
			SearchResult sr = results.next();
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("dn", sr.getNameInNamespace());

			Attributes attrs = sr.getAttributes();
			NamingEnumeration<? extends Attribute> attrEnum = attrs.getAll();
			while (attrEnum.hasMore()) {
				Attribute attr = attrEnum.next();
				if (attr.size() == 1) {
					entry.put(attr.getID(), String.valueOf(attr.get()));
				} else {
					List<String> values = new ArrayList<>();
					NamingEnumeration<?> valEnum = attr.getAll();
					while (valEnum.hasMore()) {
						values.add(String.valueOf(valEnum.next()));
					}
					entry.put(attr.getID(), values);
				}
			}

			items.add(wrapInJson(entry));
		}

		return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
	}

	// ========================= Helpers =========================

	private DirContext createDirContext(Map<String, Object> credentials) throws Exception {
		String url = String.valueOf(credentials.getOrDefault("url", "ldap://localhost:389"));
		String bindDn = String.valueOf(credentials.getOrDefault("bindDn", credentials.getOrDefault("username", "")));
		String bindPassword = String.valueOf(credentials.getOrDefault("bindPassword", credentials.getOrDefault("password", "")));
		boolean useSsl = toBoolean(credentials.get("ssl"), url.startsWith("ldaps"));

		Hashtable<String, String> env = new Hashtable<>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, url);
		env.put(Context.SECURITY_AUTHENTICATION, "simple");
		env.put(Context.SECURITY_PRINCIPAL, bindDn);
		env.put(Context.SECURITY_CREDENTIALS, bindPassword);

		if (useSsl) {
			env.put(Context.SECURITY_PROTOCOL, "ssl");
		}

		return new InitialDirContext(env);
	}

	private Map<String, Object> parseJsonAttributes(String json) {
		try {
			if (json == null || json.isBlank()) return Map.of();
			com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
			return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
		} catch (Exception e) {
			log.error("Failed to parse JSON attributes: {}", json, e);
			return Map.of();
		}
	}
}
