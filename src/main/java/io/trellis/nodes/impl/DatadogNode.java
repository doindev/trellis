package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "datadog",
	displayName = "Datadog",
	description = "Interact with the Datadog API to manage events, monitors, metrics, logs, incidents, downtimes, and SLOs.",
	category = "Analytics",
	icon = "datadog",
	credentials = {"datadogApi"}
)
public class DatadogNode extends AbstractApiNode {

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

		// ---- Resource Selector ----
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("event")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Event").value("event").description("Create, get, or list Datadog events").build(),
				ParameterOption.builder().name("Monitor").value("monitor").description("Manage Datadog monitors").build(),
				ParameterOption.builder().name("Metric").value("metric").description("Submit or query metrics").build(),
				ParameterOption.builder().name("Log").value("log").description("Submit or search logs").build(),
				ParameterOption.builder().name("Incident").value("incident").description("Manage incidents").build(),
				ParameterOption.builder().name("Downtime").value("downtime").description("Schedule and manage downtimes").build(),
				ParameterOption.builder().name("SLO").value("slo").description("Manage Service Level Objectives").build()
			)).build());

		// ---- Operation Selectors (one per resource) ----
		addOperationSelectors(params);

		// ---- Resource-Specific Parameters ----
		addEventParameters(params);
		addMonitorParameters(params);
		addMetricParameters(params);
		addLogParameters(params);
		addIncidentParameters(params);
		addDowntimeParameters(params);
		addSloParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Event operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an event").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an event by ID").build(),
				ParameterOption.builder().name("List").value("list").description("List events within a time range").build()
			)).build());

		// Monitor operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a monitor").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a monitor by ID").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a monitor").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a monitor").build(),
				ParameterOption.builder().name("Search").value("search").description("Search monitors by query").build()
			)).build());

		// Metric operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("submit")
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"))))
			.options(List.of(
				ParameterOption.builder().name("Submit").value("submit").description("Submit a metric data point").build(),
				ParameterOption.builder().name("Query").value("query").description("Query metric data").build()
			)).build());

		// Log operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("submit")
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"))))
			.options(List.of(
				ParameterOption.builder().name("Submit").value("submit").description("Submit a log entry").build(),
				ParameterOption.builder().name("Search").value("search").description("Search log entries").build()
			)).build());

		// Incident operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an incident").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an incident by ID").build(),
				ParameterOption.builder().name("List").value("list").description("List all incidents").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an incident").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an incident").build()
			)).build());

		// Downtime operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("downtime"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Schedule a downtime").build(),
				ParameterOption.builder().name("List").value("list").description("List all downtimes").build(),
				ParameterOption.builder().name("Cancel").value("cancel").description("Cancel a downtime").build()
			)).build());

		// SLO operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an SLO").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an SLO by ID").build(),
				ParameterOption.builder().name("List").value("list").description("List all SLOs").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete an SLO").build()
			)).build());
	}

	// ========================= Event Parameters =========================

	private void addEventParameters(List<NodeParameter> params) {
		// Event > Create: title
		params.add(NodeParameter.builder()
			.name("eventTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.placeHolder("Deployment completed")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
			.build());

		// Event > Create: text
		params.add(NodeParameter.builder()
			.name("eventText").displayName("Text").type(ParameterType.STRING).required(true)
			.typeOptions(Map.of("rows", 4))
			.placeHolder("Application v2.0 deployed successfully")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
			.build());

		// Event > Create: additional fields
		params.add(NodeParameter.builder()
			.name("eventAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("alertType").displayName("Alert Type")
					.type(ParameterType.OPTIONS).defaultValue("info")
					.options(List.of(
						ParameterOption.builder().name("Info").value("info").build(),
						ParameterOption.builder().name("Warning").value("warning").build(),
						ParameterOption.builder().name("Error").value("error").build(),
						ParameterOption.builder().name("Success").value("success").build()
					)).build(),
				NodeParameter.builder().name("priority").displayName("Priority")
					.type(ParameterType.OPTIONS).defaultValue("normal")
					.options(List.of(
						ParameterOption.builder().name("Normal").value("normal").build(),
						ParameterOption.builder().name("Low").value("low").build()
					)).build(),
				NodeParameter.builder().name("tags").displayName("Tags")
					.type(ParameterType.STRING).description("Comma-separated tags")
					.placeHolder("env:production,service:web").build(),
				NodeParameter.builder().name("host").displayName("Host")
					.type(ParameterType.STRING).build(),
				NodeParameter.builder().name("aggregationKey").displayName("Aggregation Key")
					.type(ParameterType.STRING).build(),
				NodeParameter.builder().name("sourceTypeName").displayName("Source Type Name")
					.type(ParameterType.STRING).build()
			)).build());

		// Event > Get: eventId
		params.add(NodeParameter.builder()
			.name("eventId").displayName("Event ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("get"))))
			.build());

		// Event > List: start
		params.add(NodeParameter.builder()
			.name("eventStart").displayName("Start (POSIX timestamp)")
			.type(ParameterType.NUMBER).required(true)
			.description("Start of the time range as a POSIX timestamp (seconds).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("list"))))
			.build());

		// Event > List: end
		params.add(NodeParameter.builder()
			.name("eventEnd").displayName("End (POSIX timestamp)")
			.type(ParameterType.NUMBER).required(true)
			.description("End of the time range as a POSIX timestamp (seconds).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("list"))))
			.build());

		// Event > List: filters
		params.add(NodeParameter.builder()
			.name("eventListFilters").displayName("Filters")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("list"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("priority").displayName("Priority")
					.type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Normal").value("normal").build(),
						ParameterOption.builder().name("Low").value("low").build()
					)).build(),
				NodeParameter.builder().name("sources").displayName("Sources")
					.type(ParameterType.STRING).description("Comma-separated source names").build(),
				NodeParameter.builder().name("tags").displayName("Tags")
					.type(ParameterType.STRING).description("Comma-separated tags to filter by")
					.placeHolder("env:production").build()
			)).build());
	}

	// ========================= Monitor Parameters =========================

	private void addMonitorParameters(List<NodeParameter> params) {
		// Monitor > Create: type
		params.add(NodeParameter.builder()
			.name("monitorType").displayName("Monitor Type")
			.type(ParameterType.OPTIONS).required(true).defaultValue("metric alert")
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Metric Alert").value("metric alert").build(),
				ParameterOption.builder().name("Service Check").value("service check").build(),
				ParameterOption.builder().name("Event Alert").value("event alert").build(),
				ParameterOption.builder().name("Event V2 Alert").value("event-v2 alert").build(),
				ParameterOption.builder().name("Query Alert").value("query alert").build(),
				ParameterOption.builder().name("Composite").value("composite").build(),
				ParameterOption.builder().name("Log Alert").value("log alert").build(),
				ParameterOption.builder().name("Trace Analytics Alert").value("trace-analytics alert").build(),
				ParameterOption.builder().name("Forecast Alert").value("forecast alert").build(),
				ParameterOption.builder().name("Process Alert").value("process alert").build()
			)).build());

		// Monitor > Create: query
		params.add(NodeParameter.builder()
			.name("monitorQuery").displayName("Query").type(ParameterType.STRING).required(true)
			.typeOptions(Map.of("rows", 3))
			.placeHolder("avg(last_5m):avg:system.cpu.user{*} > 90")
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("create"))))
			.build());

		// Monitor > Create: name
		params.add(NodeParameter.builder()
			.name("monitorName").displayName("Name").type(ParameterType.STRING)
			.placeHolder("High CPU usage")
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("create"))))
			.build());

		// Monitor > Create: message
		params.add(NodeParameter.builder()
			.name("monitorMessage").displayName("Message").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 3))
			.placeHolder("CPU usage is above 90% on {{host.name}}")
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("create"))))
			.build());

		// Monitor > Create: tags
		params.add(NodeParameter.builder()
			.name("monitorTags").displayName("Tags").type(ParameterType.STRING)
			.description("Comma-separated tags").placeHolder("env:production,team:backend")
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("create"))))
			.build());

		// Monitor > Create: priority
		params.add(NodeParameter.builder()
			.name("monitorPriority").displayName("Priority").type(ParameterType.OPTIONS)
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("P1 - Critical").value("1").build(),
				ParameterOption.builder().name("P2 - High").value("2").build(),
				ParameterOption.builder().name("P3 - Medium").value("3").build(),
				ParameterOption.builder().name("P4 - Low").value("4").build(),
				ParameterOption.builder().name("P5 - Info").value("5").build()
			)).build());

		// Monitor > Get / Update / Delete: monitorId
		params.add(NodeParameter.builder()
			.name("monitorId").displayName("Monitor ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("get", "update", "delete"))))
			.build());

		// Monitor > Update: fields
		params.add(NodeParameter.builder()
			.name("monitorUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("query").displayName("Query").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("message").displayName("Message").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("tags").displayName("Tags").type(ParameterType.STRING)
					.description("Comma-separated tags").build(),
				NodeParameter.builder().name("priority").displayName("Priority").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("P1 - Critical").value("1").build(),
						ParameterOption.builder().name("P2 - High").value("2").build(),
						ParameterOption.builder().name("P3 - Medium").value("3").build(),
						ParameterOption.builder().name("P4 - Low").value("4").build(),
						ParameterOption.builder().name("P5 - Info").value("5").build()
					)).build()
			)).build());

		// Monitor > Search: query
		params.add(NodeParameter.builder()
			.name("monitorSearchQuery").displayName("Search Query").type(ParameterType.STRING)
			.placeHolder("type:metric tag:env:production")
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("search"))))
			.build());

		// Monitor > Search: limit
		params.add(NodeParameter.builder()
			.name("monitorSearchLimit").displayName("Limit").type(ParameterType.NUMBER)
			.defaultValue(50)
			.displayOptions(Map.of("show", Map.of("resource", List.of("monitor"), "operation", List.of("search"))))
			.build());
	}

	// ========================= Metric Parameters =========================

	private void addMetricParameters(List<NodeParameter> params) {
		// Metric > Submit: name
		params.add(NodeParameter.builder()
			.name("metricName").displayName("Metric Name").type(ParameterType.STRING).required(true)
			.placeHolder("custom.metric.name")
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("submit"))))
			.build());

		// Metric > Submit: type
		params.add(NodeParameter.builder()
			.name("metricType").displayName("Metric Type")
			.type(ParameterType.OPTIONS).required(true).defaultValue("gauge")
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("submit"))))
			.options(List.of(
				ParameterOption.builder().name("Gauge").value("gauge").build(),
				ParameterOption.builder().name("Count").value("count").build(),
				ParameterOption.builder().name("Rate").value("rate").build()
			)).build());

		// Metric > Submit: value
		params.add(NodeParameter.builder()
			.name("metricValue").displayName("Value").type(ParameterType.NUMBER).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("submit"))))
			.build());

		// Metric > Submit: tags
		params.add(NodeParameter.builder()
			.name("metricTags").displayName("Tags").type(ParameterType.STRING)
			.description("Comma-separated tags").placeHolder("env:production,region:us-east")
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("submit"))))
			.build());

		// Metric > Submit: host
		params.add(NodeParameter.builder()
			.name("metricHost").displayName("Host").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("submit"))))
			.build());

		// Metric > Query: query
		params.add(NodeParameter.builder()
			.name("metricQuery").displayName("Query").type(ParameterType.STRING).required(true)
			.placeHolder("avg:system.cpu.user{*}")
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("query"))))
			.build());

		// Metric > Query: from
		params.add(NodeParameter.builder()
			.name("metricFrom").displayName("From (POSIX timestamp)")
			.type(ParameterType.NUMBER).required(true)
			.description("Start of the query time range as a POSIX timestamp (seconds).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("query"))))
			.build());

		// Metric > Query: to
		params.add(NodeParameter.builder()
			.name("metricTo").displayName("To (POSIX timestamp)")
			.type(ParameterType.NUMBER).required(true)
			.description("End of the query time range as a POSIX timestamp (seconds).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("metric"), "operation", List.of("query"))))
			.build());
	}

	// ========================= Log Parameters =========================

	private void addLogParameters(List<NodeParameter> params) {
		// Log > Submit: message
		params.add(NodeParameter.builder()
			.name("logMessage").displayName("Message").type(ParameterType.STRING).required(true)
			.typeOptions(Map.of("rows", 4))
			.placeHolder("Application started successfully")
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("submit"))))
			.build());

		// Log > Submit: additional fields
		params.add(NodeParameter.builder()
			.name("logAdditionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("submit"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("hostname").displayName("Hostname").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("service").displayName("Service").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("source").displayName("Source").type(ParameterType.STRING)
					.placeHolder("java").build(),
				NodeParameter.builder().name("tags").displayName("Tags").type(ParameterType.STRING)
					.description("Comma-separated tags").placeHolder("env:production").build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.defaultValue("info")
					.options(List.of(
						ParameterOption.builder().name("Debug").value("debug").build(),
						ParameterOption.builder().name("Info").value("info").build(),
						ParameterOption.builder().name("Warning").value("warning").build(),
						ParameterOption.builder().name("Error").value("error").build(),
						ParameterOption.builder().name("Critical").value("critical").build()
					)).build()
			)).build());

		// Log > Search: query
		params.add(NodeParameter.builder()
			.name("logSearchQuery").displayName("Query").type(ParameterType.STRING).required(true)
			.placeHolder("service:web status:error")
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("search"))))
			.build());

		// Log > Search: from
		params.add(NodeParameter.builder()
			.name("logFrom").displayName("From").type(ParameterType.STRING)
			.defaultValue("now-15m").description("Relative or ISO time (e.g. now-15m, now-1h)")
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("search"))))
			.build());

		// Log > Search: to
		params.add(NodeParameter.builder()
			.name("logTo").displayName("To").type(ParameterType.STRING)
			.defaultValue("now").description("Relative or ISO time (e.g. now)")
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("search"))))
			.build());

		// Log > Search: limit
		params.add(NodeParameter.builder()
			.name("logLimit").displayName("Limit").type(ParameterType.NUMBER).defaultValue(50)
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("search"))))
			.build());

		// Log > Search: sort
		params.add(NodeParameter.builder()
			.name("logSort").displayName("Sort").type(ParameterType.OPTIONS).defaultValue("timestamp")
			.displayOptions(Map.of("show", Map.of("resource", List.of("log"), "operation", List.of("search"))))
			.options(List.of(
				ParameterOption.builder().name("Timestamp (desc)").value("timestamp").build(),
				ParameterOption.builder().name("Timestamp (asc)").value("-timestamp").build()
			)).build());
	}

	// ========================= Incident Parameters =========================

	private void addIncidentParameters(List<NodeParameter> params) {
		// Incident > Create: title
		params.add(NodeParameter.builder()
			.name("incidentTitle").displayName("Title").type(ParameterType.STRING).required(true)
			.placeHolder("Service outage in US-East")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create"))))
			.build());

		// Incident > Create: severity
		params.add(NodeParameter.builder()
			.name("incidentSeverity").displayName("Severity")
			.type(ParameterType.OPTIONS).required(true).defaultValue("SEV-3")
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("SEV-1 (Critical)").value("SEV-1").build(),
				ParameterOption.builder().name("SEV-2 (High)").value("SEV-2").build(),
				ParameterOption.builder().name("SEV-3 (Moderate)").value("SEV-3").build(),
				ParameterOption.builder().name("SEV-4 (Low)").value("SEV-4").build(),
				ParameterOption.builder().name("SEV-5 (Minor)").value("SEV-5").build()
			)).build());

		// Incident > Create: customer impacted
		params.add(NodeParameter.builder()
			.name("customerImpacted").displayName("Customer Impacted")
			.type(ParameterType.BOOLEAN).defaultValue(false)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("create"))))
			.build());

		// Incident > Get / Update / Delete: incidentId
		params.add(NodeParameter.builder()
			.name("incidentId").displayName("Incident ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("get", "update", "delete"))))
			.build());

		// Incident > Update: fields
		params.add(NodeParameter.builder()
			.name("incidentUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("incident"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("title").displayName("Title").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("severity").displayName("Severity").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("SEV-1").value("SEV-1").build(),
						ParameterOption.builder().name("SEV-2").value("SEV-2").build(),
						ParameterOption.builder().name("SEV-3").value("SEV-3").build(),
						ParameterOption.builder().name("SEV-4").value("SEV-4").build(),
						ParameterOption.builder().name("SEV-5").value("SEV-5").build()
					)).build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Active").value("active").build(),
						ParameterOption.builder().name("Stable").value("stable").build(),
						ParameterOption.builder().name("Resolved").value("resolved").build(),
						ParameterOption.builder().name("Completed").value("completed").build()
					)).build(),
				NodeParameter.builder().name("customerImpacted").displayName("Customer Impacted")
					.type(ParameterType.BOOLEAN).build()
			)).build());
	}

	// ========================= Downtime Parameters =========================

	private void addDowntimeParameters(List<NodeParameter> params) {
		// Downtime > Create: scope
		params.add(NodeParameter.builder()
			.name("downtimeScope").displayName("Scope").type(ParameterType.STRING).required(true)
			.description("The scope to apply the downtime to (e.g. host:my-host, env:production).")
			.placeHolder("env:production")
			.displayOptions(Map.of("show", Map.of("resource", List.of("downtime"), "operation", List.of("create"))))
			.build());

		// Downtime > Create: message
		params.add(NodeParameter.builder()
			.name("downtimeMessage").displayName("Message").type(ParameterType.STRING)
			.placeHolder("Scheduled maintenance window")
			.displayOptions(Map.of("show", Map.of("resource", List.of("downtime"), "operation", List.of("create"))))
			.build());

		// Downtime > Create: start (ISO)
		params.add(NodeParameter.builder()
			.name("downtimeStart").displayName("Start Time (ISO 8601)")
			.type(ParameterType.STRING)
			.description("Start time in ISO 8601 format. Leave empty for immediate start.")
			.placeHolder("2024-01-15T00:00:00Z")
			.displayOptions(Map.of("show", Map.of("resource", List.of("downtime"), "operation", List.of("create"))))
			.build());

		// Downtime > Create: end (ISO)
		params.add(NodeParameter.builder()
			.name("downtimeEnd").displayName("End Time (ISO 8601)")
			.type(ParameterType.STRING)
			.description("End time in ISO 8601 format. Leave empty for indefinite.")
			.placeHolder("2024-01-15T06:00:00Z")
			.displayOptions(Map.of("show", Map.of("resource", List.of("downtime"), "operation", List.of("create"))))
			.build());

		// Downtime > Create: monitor tags
		params.add(NodeParameter.builder()
			.name("downtimeMonitorTags").displayName("Monitor Tags")
			.type(ParameterType.STRING)
			.description("Comma-separated monitor tags to scope the downtime.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("downtime"), "operation", List.of("create"))))
			.build());

		// Downtime > Cancel: downtimeId
		params.add(NodeParameter.builder()
			.name("downtimeId").displayName("Downtime ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("downtime"), "operation", List.of("cancel"))))
			.build());
	}

	// ========================= SLO Parameters =========================

	private void addSloParameters(List<NodeParameter> params) {
		// SLO > Create: name
		params.add(NodeParameter.builder()
			.name("sloName").displayName("Name").type(ParameterType.STRING).required(true)
			.placeHolder("API Availability SLO")
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("create"))))
			.build());

		// SLO > Create: type
		params.add(NodeParameter.builder()
			.name("sloType").displayName("Type")
			.type(ParameterType.OPTIONS).required(true).defaultValue("metric")
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Metric").value("metric").build(),
				ParameterOption.builder().name("Monitor").value("monitor").build(),
				ParameterOption.builder().name("Time Slice").value("time_slice").build()
			)).build());

		// SLO > Create: description
		params.add(NodeParameter.builder()
			.name("sloDescription").displayName("Description").type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 3))
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("create"))))
			.build());

		// SLO > Create: tags
		params.add(NodeParameter.builder()
			.name("sloTags").displayName("Tags").type(ParameterType.STRING)
			.description("Comma-separated tags").placeHolder("service:web,team:backend")
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("create"))))
			.build());

		// SLO > Create: thresholds (JSON)
		params.add(NodeParameter.builder()
			.name("sloThresholds").displayName("Thresholds (JSON)")
			.type(ParameterType.STRING).required(true)
			.typeOptions(Map.of("rows", 4))
			.description("JSON array of threshold objects, e.g. [{\"timeframe\":\"7d\",\"target\":99.9}]")
			.placeHolder("[{\"timeframe\":\"7d\",\"target\":99.9}]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("create"))))
			.build());

		// SLO > Create (monitor type): monitor IDs
		params.add(NodeParameter.builder()
			.name("sloMonitorIds").displayName("Monitor IDs")
			.type(ParameterType.STRING)
			.description("Comma-separated monitor IDs to track.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("create"), "sloType", List.of("monitor"))))
			.build());

		// SLO > Create (metric type): query
		params.add(NodeParameter.builder()
			.name("sloMetricQuery").displayName("Query")
			.type(ParameterType.STRING)
			.description("The metric query for metric-based SLOs.")
			.placeHolder("sum:my.custom.metric{*}.as_count()")
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("create"), "sloType", List.of("metric"))))
			.build());

		// SLO > Get / Delete: sloId
		params.add(NodeParameter.builder()
			.name("sloId").displayName("SLO ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("slo"), "operation", List.of("get", "delete"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "event");
		Map<String, Object> credentials = context.getCredentials();

		try {
			return switch (resource) {
				case "event" -> executeEvent(context, credentials);
				case "monitor" -> executeMonitor(context, credentials);
				case "metric" -> executeMetric(context, credentials);
				case "log" -> executeLog(context, credentials);
				case "incident" -> executeIncident(context, credentials);
				case "downtime" -> executeDowntime(context, credentials);
				case "slo" -> executeSlo(context, credentials);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Datadog API error: " + e.getMessage(), e);
		}
	}

	// ========================= Event Execute =========================

	private NodeExecutionResult executeEvent(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseApiUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String title = context.getParameter("eventTitle", "");
				String text = context.getParameter("eventText", "");
				Map<String, Object> additional = context.getParameter("eventAdditionalFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", title);
				body.put("text", text);
				putIfPresent(body, "alert_type", additional.get("alertType"));
				putIfPresent(body, "priority", additional.get("priority"));
				putTagsIfPresent(body, "tags", additional.get("tags"));
				putIfPresent(body, "host", additional.get("host"));
				putIfPresent(body, "aggregation_key", additional.get("aggregationKey"));
				putIfPresent(body, "source_type_name", additional.get("sourceTypeName"));

				HttpResponse<String> response = post(baseUrl + "/api/v1/events", body, headers);
				return toResult(response);
			}
			case "get": {
				String eventId = context.getParameter("eventId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v1/events/" + encode(eventId), headers);
				return toResult(response);
			}
			case "list": {
				long start = toLong(context.getParameter("eventStart", 0));
				long end = toLong(context.getParameter("eventEnd", 0));
				Map<String, Object> filters = context.getParameter("eventListFilters", Map.of());

				String url = baseUrl + "/api/v1/events?start=" + start + "&end=" + end;
				if (filters.get("priority") != null) url += "&priority=" + encode(String.valueOf(filters.get("priority")));
				if (filters.get("sources") != null) url += "&sources=" + encode(String.valueOf(filters.get("sources")));
				if (filters.get("tags") != null) url += "&tags=" + encode(String.valueOf(filters.get("tags")));

				HttpResponse<String> response = get(url, headers);
				return toListResult(response, "events");
			}
			default:
				return NodeExecutionResult.error("Unknown event operation: " + operation);
		}
	}

	// ========================= Monitor Execute =========================

	private NodeExecutionResult executeMonitor(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseApiUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String type = context.getParameter("monitorType", "metric alert");
				String query = context.getParameter("monitorQuery", "");
				String name = context.getParameter("monitorName", "");
				String message = context.getParameter("monitorMessage", "");
				String tags = context.getParameter("monitorTags", "");
				String priority = context.getParameter("monitorPriority", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("type", type);
				body.put("query", query);
				putIfPresent(body, "name", name);
				putIfPresent(body, "message", message);
				putTagsIfPresent(body, "tags", tags);
				if (!priority.isEmpty()) body.put("priority", Integer.parseInt(priority));

				HttpResponse<String> response = post(baseUrl + "/api/v1/monitor", body, headers);
				return toResult(response);
			}
			case "get": {
				String monitorId = context.getParameter("monitorId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v1/monitor/" + encode(monitorId), headers);
				return toResult(response);
			}
			case "update": {
				String monitorId = context.getParameter("monitorId", "");
				Map<String, Object> updateFields = context.getParameter("monitorUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "query", updateFields.get("query"));
				putIfPresent(body, "message", updateFields.get("message"));
				putTagsIfPresent(body, "tags", updateFields.get("tags"));
				if (updateFields.get("priority") != null) {
					body.put("priority", Integer.parseInt(String.valueOf(updateFields.get("priority"))));
				}

				HttpResponse<String> response = put(baseUrl + "/api/v1/monitor/" + encode(monitorId), body, headers);
				return toResult(response);
			}
			case "delete": {
				String monitorId = context.getParameter("monitorId", "");
				HttpResponse<String> response = delete(baseUrl + "/api/v1/monitor/" + encode(monitorId), headers);
				return toDeleteResult(response);
			}
			case "search": {
				String query = context.getParameter("monitorSearchQuery", "");
				int limit = toInt(context.getParameter("monitorSearchLimit", 50), 50);
				String url = baseUrl + "/api/v1/monitor/search?query=" + encode(query) + "&per_page=" + limit;
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown monitor operation: " + operation);
		}
	}

	// ========================= Metric Execute =========================

	private NodeExecutionResult executeMetric(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "submit");
		String baseUrl = getBaseApiUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "submit": {
				String name = context.getParameter("metricName", "");
				String type = context.getParameter("metricType", "gauge");
				double value = toDouble(context.getParameter("metricValue", 0));
				String tags = context.getParameter("metricTags", "");
				String host = context.getParameter("metricHost", "");

				long timestamp = System.currentTimeMillis() / 1000;

				Map<String, Object> point = new LinkedHashMap<>();
				point.put("timestamp", timestamp);
				point.put("value", value);

				Map<String, Object> series = new LinkedHashMap<>();
				series.put("metric", name);
				series.put("type", mapMetricType(type));
				series.put("points", List.of(point));
				if (!tags.isEmpty()) series.put("tags", parseTags(tags));
				putIfPresent(series, "host", host);

				Map<String, Object> body = Map.of("series", List.of(series));

				HttpResponse<String> response = post(baseUrl + "/api/v2/series", body, headers);
				return toResult(response);
			}
			case "query": {
				String query = context.getParameter("metricQuery", "");
				long from = toLong(context.getParameter("metricFrom", 0));
				long to = toLong(context.getParameter("metricTo", 0));

				String url = baseUrl + "/api/v1/query?query=" + encode(query)
					+ "&from=" + from + "&to=" + to;
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown metric operation: " + operation);
		}
	}

	// ========================= Log Execute =========================

	private NodeExecutionResult executeLog(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "submit");
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "submit": {
				String message = context.getParameter("logMessage", "");
				Map<String, Object> additional = context.getParameter("logAdditionalFields", Map.of());

				Map<String, Object> logEntry = new LinkedHashMap<>();
				logEntry.put("message", message);
				putIfPresent(logEntry, "hostname", additional.get("hostname"));
				putIfPresent(logEntry, "service", additional.get("service"));
				putIfPresent(logEntry, "ddsource", additional.get("source"));
				putIfPresent(logEntry, "status", additional.get("status"));
				putTagsIfPresent(logEntry, "ddtags", additional.get("tags"));

				String logsUrl = getLogsIntakeUrl(credentials);
				HttpResponse<String> response = post(logsUrl, List.of(logEntry), headers);
				return toResult(response);
			}
			case "search": {
				String baseUrl = getBaseApiUrl(credentials);
				String query = context.getParameter("logSearchQuery", "");
				String from = context.getParameter("logFrom", "now-15m");
				String to = context.getParameter("logTo", "now");
				int limit = toInt(context.getParameter("logLimit", 50), 50);
				String sort = context.getParameter("logSort", "timestamp");

				Map<String, Object> filter = new LinkedHashMap<>();
				filter.put("query", query);
				filter.put("from", from);
				filter.put("to", to);

				Map<String, Object> page = Map.of("limit", limit);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("filter", filter);
				body.put("sort", sort);
				body.put("page", page);

				HttpResponse<String> response = post(baseUrl + "/api/v2/logs/events/search", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown log operation: " + operation);
		}
	}

	// ========================= Incident Execute =========================

	@SuppressWarnings("unchecked")
	private NodeExecutionResult executeIncident(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseApiUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String title = context.getParameter("incidentTitle", "");
				String severity = context.getParameter("incidentSeverity", "SEV-3");
				boolean impacted = toBoolean(context.getParameter("customerImpacted", false), false);

				Map<String, Object> attributes = new LinkedHashMap<>();
				attributes.put("title", title);
				attributes.put("customer_impacted", impacted);
				Map<String, Object> fields = Map.of("severity", Map.of("type", "dropdown", "value", severity));
				attributes.put("fields", fields);

				Map<String, Object> body = jsonApiBody("incidents", attributes);
				HttpResponse<String> response = post(baseUrl + "/api/v2/incidents", body, headers);
				return toResult(response);
			}
			case "get": {
				String incidentId = context.getParameter("incidentId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v2/incidents/" + encode(incidentId), headers);
				return toResult(response);
			}
			case "list": {
				HttpResponse<String> response = get(baseUrl + "/api/v2/incidents", headers);
				return toListResult(response, "data");
			}
			case "update": {
				String incidentId = context.getParameter("incidentId", "");
				Map<String, Object> updateFields = context.getParameter("incidentUpdateFields", Map.of());

				Map<String, Object> attributes = new LinkedHashMap<>();
				putIfPresent(attributes, "title", updateFields.get("title"));
				if (updateFields.get("customerImpacted") != null) {
					attributes.put("customer_impacted", toBoolean(updateFields.get("customerImpacted"), false));
				}

				Map<String, Object> fields = new LinkedHashMap<>();
				if (updateFields.get("severity") != null) {
					fields.put("severity", Map.of("type", "dropdown", "value", updateFields.get("severity")));
				}
				if (updateFields.get("status") != null) {
					fields.put("state", Map.of("type", "dropdown", "value", updateFields.get("status")));
				}
				if (!fields.isEmpty()) {
					attributes.put("fields", fields);
				}

				Map<String, Object> data = new LinkedHashMap<>();
				data.put("id", incidentId);
				data.put("type", "incidents");
				data.put("attributes", attributes);

				HttpResponse<String> response = patch(baseUrl + "/api/v2/incidents/" + encode(incidentId),
					Map.of("data", data), headers);
				return toResult(response);
			}
			case "delete": {
				String incidentId = context.getParameter("incidentId", "");
				HttpResponse<String> response = delete(baseUrl + "/api/v2/incidents/" + encode(incidentId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown incident operation: " + operation);
		}
	}

	// ========================= Downtime Execute =========================

	private NodeExecutionResult executeDowntime(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseApiUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String scope = context.getParameter("downtimeScope", "");
				String message = context.getParameter("downtimeMessage", "");
				String start = context.getParameter("downtimeStart", "");
				String end = context.getParameter("downtimeEnd", "");
				String monitorTags = context.getParameter("downtimeMonitorTags", "");

				Map<String, Object> attributes = new LinkedHashMap<>();
				attributes.put("scope", scope);
				putIfPresent(attributes, "message", message);

				Map<String, Object> schedule = new LinkedHashMap<>();
				if (!start.isEmpty()) schedule.put("start", start);
				if (!end.isEmpty()) schedule.put("end", end);
				if (!schedule.isEmpty()) attributes.put("schedule", schedule);

				if (!monitorTags.isEmpty()) {
					attributes.put("monitor_tags", parseTags(monitorTags));
				}

				Map<String, Object> body = jsonApiBody("downtime", attributes);
				HttpResponse<String> response = post(baseUrl + "/api/v2/downtime", body, headers);
				return toResult(response);
			}
			case "list": {
				HttpResponse<String> response = get(baseUrl + "/api/v2/downtime", headers);
				return toListResult(response, "data");
			}
			case "cancel": {
				String downtimeId = context.getParameter("downtimeId", "");
				HttpResponse<String> response = delete(baseUrl + "/api/v2/downtime/" + encode(downtimeId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown downtime operation: " + operation);
		}
	}

	// ========================= SLO Execute =========================

	private NodeExecutionResult executeSlo(NodeExecutionContext context, Map<String, Object> credentials) throws Exception {
		String operation = context.getParameter("operation", "create");
		String baseUrl = getBaseApiUrl(credentials);
		Map<String, String> headers = getAuthHeaders(credentials);

		switch (operation) {
			case "create": {
				String name = context.getParameter("sloName", "");
				String type = context.getParameter("sloType", "metric");
				String description = context.getParameter("sloDescription", "");
				String tags = context.getParameter("sloTags", "");
				String thresholdsJson = context.getParameter("sloThresholds", "[]");
				String monitorIds = context.getParameter("sloMonitorIds", "");
				String metricQuery = context.getParameter("sloMetricQuery", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("type", type);
				putIfPresent(body, "description", description);
				putTagsIfPresent(body, "tags", tags);

				// Parse thresholds JSON
				try {
					Object thresholds = objectMapper.readValue(thresholdsJson, Object.class);
					body.put("thresholds", thresholds);
				} catch (Exception e) {
					return NodeExecutionResult.error("Invalid thresholds JSON: " + e.getMessage());
				}

				if ("monitor".equals(type) && !monitorIds.isEmpty()) {
					List<Long> ids = Arrays.stream(monitorIds.split(","))
						.map(String::trim).filter(s -> !s.isEmpty())
						.map(Long::parseLong)
						.toList();
					body.put("monitor_ids", ids);
				}
				if ("metric".equals(type) && !metricQuery.isEmpty()) {
					body.put("query", Map.of(
						"numerator", metricQuery,
						"denominator", metricQuery
					));
				}

				HttpResponse<String> response = post(baseUrl + "/api/v1/slo", body, headers);
				return toResult(response);
			}
			case "get": {
				String sloId = context.getParameter("sloId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v1/slo/" + encode(sloId), headers);
				return toResult(response);
			}
			case "list": {
				HttpResponse<String> response = get(baseUrl + "/api/v1/slo", headers);
				return toListResult(response, "data");
			}
			case "delete": {
				String sloId = context.getParameter("sloId", "");
				HttpResponse<String> response = delete(baseUrl + "/api/v1/slo/" + encode(sloId), headers);
				return toDeleteResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown SLO operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("DD-API-KEY", String.valueOf(credentials.getOrDefault("apiKey", "")));
		headers.put("DD-APPLICATION-KEY", String.valueOf(credentials.getOrDefault("applicationKey", "")));
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private String getBaseApiUrl(Map<String, Object> credentials) {
		String site = String.valueOf(credentials.getOrDefault("site", "datadoghq.com"));
		return "https://api." + site;
	}

	private String getLogsIntakeUrl(Map<String, Object> credentials) {
		String site = String.valueOf(credentials.getOrDefault("site", "datadoghq.com"));
		return "https://http-intake.logs." + site + "/api/v2/logs";
	}

	private List<String> parseTags(String tags) {
		if (tags == null || tags.isBlank()) return List.of();
		return Arrays.stream(tags.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toList();
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	private void putTagsIfPresent(Map<String, Object> map, String key, Object tagsValue) {
		if (tagsValue != null && !String.valueOf(tagsValue).isEmpty()) {
			if (tagsValue instanceof List) {
				map.put(key, tagsValue);
			} else {
				map.put(key, parseTags(String.valueOf(tagsValue)));
			}
		}
	}

	private int mapMetricType(String type) {
		return switch (type) {
			case "count" -> 1;
			case "rate" -> 2;
			default -> 0; // gauge
		};
	}

	private Map<String, Object> jsonApiBody(String type, Map<String, Object> attributes) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("type", type);
		data.put("attributes", attributes);
		return Map.of("data", data);
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return datadogError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return datadogError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return datadogError(response);
		}
		// DELETE may return 204 No Content
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult datadogError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Datadog API error (HTTP " + response.statusCode() + "): " + body);
	}

	private long toLong(Object value) {
		if (value instanceof Number) return ((Number) value).longValue();
		if (value instanceof String) {
			try { return Long.parseLong((String) value); } catch (NumberFormatException e) { return 0; }
		}
		return 0;
	}

	private double toDouble(Object value) {
		if (value instanceof Number) return ((Number) value).doubleValue();
		if (value instanceof String) {
			try { return Double.parseDouble((String) value); } catch (NumberFormatException e) { return 0; }
		}
		return 0;
	}
}
