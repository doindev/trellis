package io.trellis.nodes.impl;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;

import io.trellis.entity.DataTableEntity;
import io.trellis.entity.DataTableRowEntity;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.service.DataTableService;
import io.trellis.service.DataTableService.RowFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * Data Table Node - provides persistent data storage across workflow executions.
 * Uses a resource/operation pattern (actions sub menu):
 *   Resource: Row -> Operations: Insert, Get, Delete, Update, Upsert
 *   Resource: Table -> Operations: Create, Delete, List
 */
@Slf4j
@Node(
	type = "dataTable",
	displayName = "Data Table",
	description = "Permanently save data across workflow executions in a table.",
	category = "Core",
	icon = "table-2"
)
public class DataTableNode extends AbstractNode {

	@Autowired
	private DataTableService dataTableService;

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
		return List.of(
			// ---- Resource Selector (actions sub menu) ----
			NodeParameter.builder()
				.name("resource")
				.displayName("Resource")
				.description("The resource to operate on.")
				.type(ParameterType.OPTIONS)
				.defaultValue("row")
				.noDataExpression(true)
				.options(List.of(
					ParameterOption.builder().name("Row").value("row")
						.description("Work with rows in a data table").build(),
					ParameterOption.builder().name("Table").value("table")
						.description("Manage data tables").build()
				))
				.build(),

			// ---- Row Operations ----
			NodeParameter.builder()
				.name("rowOperation")
				.displayName("Operation")
				.description("The row operation to perform.")
				.type(ParameterType.OPTIONS)
				.defaultValue("get")
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"))))
				.options(List.of(
					ParameterOption.builder().name("Insert Row").value("insert")
						.description("Insert a new row from input data").action("Insert a row").build(),
					ParameterOption.builder().name("Get Rows").value("get")
						.description("Get rows from the table, optionally filtered").action("Get rows").build(),
					ParameterOption.builder().name("Delete Rows").value("deleteRows")
						.description("Delete rows matching conditions").action("Delete rows").build(),
					ParameterOption.builder().name("Update Rows").value("update")
						.description("Update rows matching conditions with new values").action("Update rows").build(),
					ParameterOption.builder().name("Upsert Row").value("upsert")
						.description("Update a matching row or insert a new one").action("Upsert a row").build()
				))
				.build(),

			// ---- Table Operations ----
			NodeParameter.builder()
				.name("tableOperation")
				.displayName("Operation")
				.description("The table operation to perform.")
				.type(ParameterType.OPTIONS)
				.defaultValue("create")
				.displayOptions(Map.of("show", Map.of("resource", List.of("table"))))
				.options(List.of(
					ParameterOption.builder().name("Create Table").value("create")
						.description("Create a new data table with specified columns").action("Create a table").build(),
					ParameterOption.builder().name("Delete Table").value("deleteTable")
						.description("Permanently delete a data table and all its rows").action("Delete a table").build(),
					ParameterOption.builder().name("List Tables").value("list")
						.description("List all available data tables").action("List tables").build()
				))
				.build(),

			// ---- Data Table ID (for row operations + table delete) ----
			NodeParameter.builder()
				.name("dataTableId")
				.displayName("Data Table")
				.description("The ID or name of the data table to operate on.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("Table ID or name")
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"))))
				.build(),

			NodeParameter.builder()
				.name("dataTableId")
				.displayName("Data Table")
				.description("The ID or name of the data table.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("Table ID or name")
				.displayOptions(Map.of("show", Map.of("resource", List.of("table"),
					"tableOperation", List.of("deleteTable"))))
				.build(),

			// ---- Row > Get: Filters ----
			NodeParameter.builder()
				.name("matchType")
				.displayName("Match")
				.description("How to match conditions.")
				.type(ParameterType.OPTIONS)
				.defaultValue("allConditions")
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"),
					"rowOperation", List.of("get", "deleteRows", "update", "upsert"))))
				.options(List.of(
					ParameterOption.builder().name("All Conditions (AND)").value("allConditions").build(),
					ParameterOption.builder().name("Any Condition (OR)").value("anyCondition").build()
				))
				.build(),

			NodeParameter.builder()
				.name("filters")
				.displayName("Conditions")
				.description("Filter conditions to match rows. Leave empty to match all rows (for Get) or none (for Delete/Update).")
				.type(ParameterType.FIXED_COLLECTION)
				.defaultValue(Map.of())
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"),
					"rowOperation", List.of("get", "deleteRows", "update", "upsert"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("column")
						.displayName("Column")
						.description("The column name to filter on.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. email")
						.build(),
					NodeParameter.builder()
						.name("condition")
						.displayName("Condition")
						.description("The comparison operator.")
						.type(ParameterType.OPTIONS)
						.defaultValue("eq")
						.options(List.of(
							ParameterOption.builder().name("Equals").value("eq").build(),
							ParameterOption.builder().name("Not Equals").value("neq").build(),
							ParameterOption.builder().name("Contains").value("contains").build(),
							ParameterOption.builder().name("Not Contains").value("notContains").build(),
							ParameterOption.builder().name("Greater Than").value("gt").build(),
							ParameterOption.builder().name("Greater or Equal").value("gte").build(),
							ParameterOption.builder().name("Less Than").value("lt").build(),
							ParameterOption.builder().name("Less or Equal").value("lte").build(),
							ParameterOption.builder().name("Is Empty").value("isEmpty").build(),
							ParameterOption.builder().name("Is Not Empty").value("isNotEmpty").build(),
							ParameterOption.builder().name("Is True").value("isTrue").build(),
							ParameterOption.builder().name("Is False").value("isFalse").build()
						))
						.build(),
					NodeParameter.builder()
						.name("value")
						.displayName("Value")
						.description("The value to compare against.")
						.type(ParameterType.STRING)
						.placeHolder("comparison value")
						.build()
				))
				.build(),

			// ---- Row > Get: Limit / Sort ----
			NodeParameter.builder()
				.name("returnAll")
				.displayName("Return All")
				.description("Whether to return all matching rows or limit the result.")
				.type(ParameterType.BOOLEAN)
				.defaultValue(false)
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"),
					"rowOperation", List.of("get"))))
				.build(),

			NodeParameter.builder()
				.name("limit")
				.displayName("Limit")
				.description("Maximum number of rows to return.")
				.type(ParameterType.NUMBER)
				.defaultValue(50)
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"),
					"rowOperation", List.of("get"), "returnAll", List.of(false))))
				.build(),

			NodeParameter.builder()
				.name("orderBy")
				.displayName("Order By Column")
				.description("Column name to sort results by. Leave empty for default (createdAt).")
				.type(ParameterType.STRING)
				.placeHolder("createdAt")
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"),
					"rowOperation", List.of("get"))))
				.build(),

			NodeParameter.builder()
				.name("orderDirection")
				.displayName("Sort Direction")
				.description("Sort direction.")
				.type(ParameterType.OPTIONS)
				.defaultValue("desc")
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"),
					"rowOperation", List.of("get"))))
				.options(List.of(
					ParameterOption.builder().name("Ascending").value("asc").build(),
					ParameterOption.builder().name("Descending").value("desc").build()
				))
				.build(),

			// ---- Row > Upsert: Match Column ----
			NodeParameter.builder()
				.name("matchColumn")
				.displayName("Match Column")
				.description("The column to use for matching existing rows. If a row with this column value exists, it is updated; otherwise a new row is inserted.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("e.g. id or email")
				.displayOptions(Map.of("show", Map.of("resource", List.of("row"),
					"rowOperation", List.of("upsert"))))
				.build(),

			// ---- Table > Create: Name + Columns ----
			NodeParameter.builder()
				.name("tableName")
				.displayName("Table Name")
				.description("The name for the new data table.")
				.type(ParameterType.STRING)
				.required(true)
				.placeHolder("e.g. My Data Table")
				.displayOptions(Map.of("show", Map.of("resource", List.of("table"),
					"tableOperation", List.of("create"))))
				.build(),

			NodeParameter.builder()
				.name("tableColumns")
				.displayName("Columns")
				.description("Define the columns for the new table.")
				.type(ParameterType.FIXED_COLLECTION)
				.defaultValue(Map.of())
				.displayOptions(Map.of("show", Map.of("resource", List.of("table"),
					"tableOperation", List.of("create"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("name")
						.displayName("Name")
						.description("Column name.")
						.type(ParameterType.STRING)
						.required(true)
						.placeHolder("e.g. email")
						.build(),
					NodeParameter.builder()
						.name("type")
						.displayName("Type")
						.description("Column data type.")
						.type(ParameterType.OPTIONS)
						.defaultValue("string")
						.options(List.of(
							ParameterOption.builder().name("String").value("string").build(),
							ParameterOption.builder().name("Number").value("number").build(),
							ParameterOption.builder().name("Boolean").value("boolean").build(),
							ParameterOption.builder().name("Date").value("date").build()
						))
						.build()
				))
				.build(),

			// ---- Table > Create: Options ----
			NodeParameter.builder()
				.name("createOptions")
				.displayName("Options")
				.description("Additional options for table creation.")
				.type(ParameterType.COLLECTION)
				.defaultValue(Map.of())
				.displayOptions(Map.of("show", Map.of("resource", List.of("table"),
					"tableOperation", List.of("create"))))
				.nestedParameters(List.of(
					NodeParameter.builder()
						.name("reuseExisting")
						.displayName("Reuse Existing Table")
						.description("If a table with the same name exists, return it instead of throwing an error.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(true)
						.build()
				))
				.build(),

			// ---- Table > Delete: Warning ----
			NodeParameter.builder()
				.name("deleteWarning")
				.displayName("")
				.description("This will permanently delete the data table and all its data. This action cannot be undone.")
				.type(ParameterType.NOTICE)
				.displayOptions(Map.of("show", Map.of("resource", List.of("table"),
					"tableOperation", List.of("deleteTable"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "row");

		try {
			if ("table".equals(resource)) {
				return executeTableOperation(context);
			} else {
				return executeRowOperation(context);
			}
		} catch (Exception e) {
			return handleError(context, "Data Table error: " + e.getMessage(), e);
		}
	}

	// ---- Table Operations ----

	private NodeExecutionResult executeTableOperation(NodeExecutionContext context) {
		String operation = context.getParameter("tableOperation", "create");

		switch (operation) {
			case "create": return executeCreateTable(context);
			case "deleteTable": return executeDeleteTable(context);
			case "list": return executeListTables(context);
			default: return NodeExecutionResult.error("Unknown table operation: " + operation);
		}
	}

	private NodeExecutionResult executeCreateTable(NodeExecutionContext context) {
		String tableName = context.getParameter("tableName", "");
		if (tableName.isBlank()) {
			return NodeExecutionResult.error("Table name is required.");
		}

		Map<String, Object> options = context.getParameter("createOptions", Map.of());
		boolean reuseExisting = Boolean.TRUE.equals(options.getOrDefault("reuseExisting", true));

		// Parse columns from FIXED_COLLECTION
		List<Map<String, String>> columns = parseFixedCollectionEntries(context, "tableColumns");

		if (reuseExisting) {
			Optional<DataTableEntity> existing = dataTableService.findTableByName(tableName);
			if (existing.isPresent()) {
				log.debug("Data Table: reusing existing table '{}'", tableName);
				return NodeExecutionResult.success(List.of(wrapInJson(tableToMap(existing.get()))));
			}
		}

		DataTableEntity table = dataTableService.createTable(tableName, columns);
		return NodeExecutionResult.success(List.of(wrapInJson(tableToMap(table))));
	}

	private NodeExecutionResult executeDeleteTable(NodeExecutionContext context) {
		String tableId = resolveTableId(context);
		if (tableId == null) {
			return NodeExecutionResult.error("Data table not found.");
		}

		dataTableService.deleteTable(tableId);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("deletedTableId", tableId);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeListTables(NodeExecutionContext context) {
		List<DataTableEntity> tables = dataTableService.listTables();
		List<Map<String, Object>> items = tables.stream()
				.map(t -> wrapInJson(tableToMap(t)))
				.toList();
		return NodeExecutionResult.success(items.isEmpty() ? List.of() : items);
	}

	// ---- Row Operations ----

	private NodeExecutionResult executeRowOperation(NodeExecutionContext context) {
		String operation = context.getParameter("rowOperation", "get");

		switch (operation) {
			case "insert": return executeInsertRow(context);
			case "get": return executeGetRows(context);
			case "deleteRows": return executeDeleteRows(context);
			case "update": return executeUpdateRows(context);
			case "upsert": return executeUpsertRow(context);
			default: return NodeExecutionResult.error("Unknown row operation: " + operation);
		}
	}

	private NodeExecutionResult executeInsertRow(NodeExecutionContext context) {
		String tableId = resolveTableId(context);
		if (tableId == null) {
			return NodeExecutionResult.error("Data table not found.");
		}

		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.error("No input data to insert.");
		}

		List<Map<String, Object>> dataToInsert = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = unwrapJson(item);
			// Remove internal metadata fields
			Map<String, Object> clean = new LinkedHashMap<>(json);
			clean.remove("_inputIndex");
			clean.remove("_triggerTimestamp");
			dataToInsert.add(clean);
		}

		List<DataTableRowEntity> inserted = dataTableService.insertRows(tableId, dataToInsert);
		List<Map<String, Object>> output = inserted.stream()
				.map(this::rowToOutputItem)
				.toList();

		log.debug("Data Table: inserted {} rows into table {}", inserted.size(), tableId);
		return NodeExecutionResult.success(output);
	}

	private NodeExecutionResult executeGetRows(NodeExecutionContext context) {
		String tableId = resolveTableId(context);
		if (tableId == null) {
			return NodeExecutionResult.error("Data table not found.");
		}

		List<RowFilter> filters = parseFilters(context);
		String matchType = context.getParameter("matchType", "allConditions");
		boolean returnAll = Boolean.TRUE.equals(context.getParameter("returnAll", false));
		int limit = returnAll ? 0 : toInt(context.getParameter("limit", 50), 50);
		String orderBy = context.getParameter("orderBy", "");
		String orderDirection = context.getParameter("orderDirection", "desc");

		List<DataTableRowEntity> rows = dataTableService.getRows(
				tableId, filters, matchType, returnAll ? null : limit,
				orderBy.isBlank() ? null : orderBy, orderDirection);

		List<Map<String, Object>> output = rows.stream()
				.map(this::rowToOutputItem)
				.toList();

		log.debug("Data Table: retrieved {} rows from table {}", output.size(), tableId);
		return NodeExecutionResult.success(output);
	}

	private NodeExecutionResult executeDeleteRows(NodeExecutionContext context) {
		String tableId = resolveTableId(context);
		if (tableId == null) {
			return NodeExecutionResult.error("Data table not found.");
		}

		List<RowFilter> filters = parseFilters(context);
		if (filters.isEmpty()) {
			return NodeExecutionResult.error("At least one condition is required to delete rows.");
		}

		String matchType = context.getParameter("matchType", "allConditions");
		int deleted = dataTableService.deleteRows(tableId, filters, matchType);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("deletedCount", deleted);
		log.debug("Data Table: deleted {} rows from table {}", deleted, tableId);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult executeUpdateRows(NodeExecutionContext context) {
		String tableId = resolveTableId(context);
		if (tableId == null) {
			return NodeExecutionResult.error("Data table not found.");
		}

		List<RowFilter> filters = parseFilters(context);
		if (filters.isEmpty()) {
			return NodeExecutionResult.error("At least one condition is required to update rows.");
		}

		String matchType = context.getParameter("matchType", "allConditions");

		// Get update data from the first input item
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.error("No input data for update values.");
		}

		Map<String, Object> updateData = new LinkedHashMap<>(unwrapJson(inputData.get(0)));
		updateData.remove("_inputIndex");
		updateData.remove("_triggerTimestamp");

		List<DataTableRowEntity> updated = dataTableService.updateRows(tableId, filters, matchType, updateData);
		List<Map<String, Object>> output = updated.stream()
				.map(this::rowToOutputItem)
				.toList();

		log.debug("Data Table: updated {} rows in table {}", updated.size(), tableId);
		return NodeExecutionResult.success(output);
	}

	private NodeExecutionResult executeUpsertRow(NodeExecutionContext context) {
		String tableId = resolveTableId(context);
		if (tableId == null) {
			return NodeExecutionResult.error("Data table not found.");
		}

		String matchColumn = context.getParameter("matchColumn", "");
		if (matchColumn.isBlank()) {
			return NodeExecutionResult.error("Match column is required for upsert.");
		}

		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			return NodeExecutionResult.error("No input data for upsert.");
		}

		List<Map<String, Object>> output = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> data = new LinkedHashMap<>(unwrapJson(item));
			data.remove("_inputIndex");
			data.remove("_triggerTimestamp");

			DataTableRowEntity row = dataTableService.upsertRow(tableId, matchColumn, data);
			output.add(rowToOutputItem(row));
		}

		log.debug("Data Table: upserted {} rows in table {}", output.size(), tableId);
		return NodeExecutionResult.success(output);
	}

	// ---- Helpers ----

	/**
	 * Resolve the data table by ID or name.
	 */
	private String resolveTableId(NodeExecutionContext context) {
		String value = context.getParameter("dataTableId", "");
		if (value.isBlank()) return null;

		// Try as ID first
		try {
			dataTableService.getTable(value);
			return value;
		} catch (Exception ignored) {}

		// Try as name
		return dataTableService.findTableByName(value)
				.map(DataTableEntity::getId)
				.orElse(null);
	}

	@SuppressWarnings("unchecked")
	private List<RowFilter> parseFilters(NodeExecutionContext context) {
		List<RowFilter> filters = new ArrayList<>();
		try {
			Object filtersObj = context.getParameters().get("filters");
			if (filtersObj instanceof Map) {
				Object values = ((Map<String, Object>) filtersObj).get("values");
				if (values instanceof List) {
					for (Object entry : (List<?>) values) {
						if (entry instanceof Map) {
							Map<String, Object> f = (Map<String, Object>) entry;
							String column = (String) f.get("column");
							String condition = (String) f.getOrDefault("condition", "eq");
							Object value = f.get("value");
							if (column != null && !column.isBlank()) {
								filters.add(new RowFilter(column, condition, value));
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.warn("Failed to parse filters: {}", e.getMessage());
		}
		return filters;
	}

	@SuppressWarnings("unchecked")
	private <T> List<Map<String, T>> parseFixedCollectionEntries(NodeExecutionContext context, String paramName) {
		List<Map<String, T>> entries = new ArrayList<>();
		try {
			Object obj = context.getParameters().get(paramName);
			if (obj instanceof Map) {
				Object values = ((Map<String, Object>) obj).get("values");
				if (values instanceof List) {
					for (Object entry : (List<?>) values) {
						if (entry instanceof Map) {
							entries.add((Map<String, T>) entry);
						}
					}
				}
			}
		} catch (Exception e) {
			log.warn("Failed to parse fixed collection '{}': {}", paramName, e.getMessage());
		}
		return entries;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> rowToOutputItem(DataTableRowEntity row) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("id", row.getId());
		if (row.getRowData() instanceof Map) {
			data.putAll((Map<String, Object>) row.getRowData());
		}
		data.put("createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
		data.put("updatedAt", row.getUpdatedAt() != null ? row.getUpdatedAt().toString() : null);
		return wrapInJson(data);
	}

	private Map<String, Object> tableToMap(DataTableEntity table) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", table.getId());
		map.put("name", table.getName());
		map.put("columns", table.getColumnDefinitions());
		map.put("createdAt", table.getCreatedAt() != null ? table.getCreatedAt().toString() : null);
		map.put("updatedAt", table.getUpdatedAt() != null ? table.getUpdatedAt().toString() : null);
		return map;
	}
}
