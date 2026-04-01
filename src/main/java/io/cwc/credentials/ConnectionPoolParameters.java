package io.cwc.credentials;

import java.util.Map;

import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Shared Tomcat JDBC connection pool parameters for all JDBC-based database credentials.
 */
public final class ConnectionPoolParameters {

    private ConnectionPoolParameters() {}

    /**
     * Returns a COLLECTION parameter named "connectionPool" containing all configurable
     * Tomcat JDBC pool properties with sensible defaults.
     */
    public static NodeParameter connectionPoolParameter() {
        return NodeParameter.builder()
                .name("connectionPool")
                .displayName("Connection Pool")
                .description("Tomcat JDBC connection pool settings")
                .type(ParameterType.COLLECTION)
                .required(false)
                .nestedParameters(java.util.List.of(
                        // --- Pool Sizing ---
                        NodeParameter.builder()
                                .name("initialSize").displayName("Initial Size")
                                .description("Number of connections created when the pool starts")
                                .type(ParameterType.NUMBER).defaultValue(0)
                                .minValue(0).build(),
                        NodeParameter.builder()
                                .name("maxActive").displayName("Max Active")
                                .description("Maximum number of active connections allocated at once")
                                .type(ParameterType.NUMBER).defaultValue(10)
                                .minValue(1).build(),
                        NodeParameter.builder()
                                .name("maxIdle").displayName("Max Idle")
                                .description("Maximum number of idle connections in the pool")
                                .type(ParameterType.NUMBER).defaultValue(10)
                                .minValue(0).build(),
                        NodeParameter.builder()
                                .name("minIdle").displayName("Min Idle")
                                .description("Minimum number of idle connections the pool maintains")
                                .type(ParameterType.NUMBER).defaultValue(1)
                                .minValue(0).build(),
                        NodeParameter.builder()
                                .name("maxWaitMillis").displayName("Max Wait (ms)")
                                .description("Maximum time in milliseconds to wait for a connection before throwing an exception")
                                .type(ParameterType.NUMBER).defaultValue(30000)
                                .minValue(-1).build(),

                        // --- Validation ---
                        NodeParameter.builder()
                                .name("validationQuery").displayName("Validation Query")
                                .description("SQL query used to test if a connection is alive")
                                .type(ParameterType.STRING).defaultValue("SELECT 1")
                                .build(),
                        NodeParameter.builder()
                                .name("testOnBorrow").displayName("Test On Borrow")
                                .description("Validate connections before giving them to the application")
                                .type(ParameterType.BOOLEAN).defaultValue(false)
                                .build(),
                        NodeParameter.builder()
                                .name("testWhileIdle").displayName("Test While Idle")
                                .description("Validate idle connections in the background")
                                .type(ParameterType.BOOLEAN).defaultValue(false)
                                .build(),
                        NodeParameter.builder()
                                .name("validationInterval").displayName("Validation Interval (ms)")
                                .description("Minimum time between validation queries on the same connection")
                                .type(ParameterType.NUMBER).defaultValue(3000)
                                .minValue(0).build(),

                        // --- Leak Detection ---
                        NodeParameter.builder()
                                .name("removeAbandoned").displayName("Remove Abandoned")
                                .description("Reclaim connections that have been checked out longer than the abandoned timeout")
                                .type(ParameterType.BOOLEAN).defaultValue(false)
                                .build(),
                        NodeParameter.builder()
                                .name("removeAbandonedTimeout").displayName("Abandoned Timeout (seconds)")
                                .description("Time in seconds before an active connection is considered abandoned")
                                .type(ParameterType.NUMBER).defaultValue(60)
                                .minValue(1).build(),
                        NodeParameter.builder()
                                .name("logAbandoned").displayName("Log Abandoned")
                                .description("Log a stack trace of the code that borrowed an abandoned connection")
                                .type(ParameterType.BOOLEAN).defaultValue(false)
                                .build()
                ))
                .build();
    }

    /**
     * Extracts a pool setting from the credentials map, falling back to a default.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getPoolConfig(Map<String, Object> credentials) {
        Object pool = credentials.get("connectionPool");
        if (pool instanceof Map) {
            return (Map<String, Object>) pool;
        }
        return Map.of();
    }
}
