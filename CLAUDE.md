# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Trellis is a workflow automation platform (similar to n8n/Make/Zapier) with a Java Spring Boot backend and Angular frontend. It features a node-based execution engine where workflow "nodes" are auto-discovered Spring components that process data through configurable pipelines.

## Build & Run Commands

```bash
mvn clean install            # Full build (Java + Angular frontend)
mvn spring-boot:run          # Run app (port 5678)
mvn test                     # Run tests
mvn clean compile            # Compile Java only (skip frontend)
```

Frontend is built automatically via `frontend-maven-plugin` (Node v20.18.0). The Angular app lives in `frontend/` and npm uses `--legacy-peer-deps`.

## Tech Stack

- **Java 17** / **Spring Boot 3.5.10** / **Maven**
- **Angular** frontend (built into Spring Boot JAR)
- **H2** in PostgreSQL compatibility mode (dev database)
- **Lombok** for boilerplate reduction (uses `@Data`, `@Builder`, `@Slf4j`)
- **LangChain4j 1.11.0** for LLM/AI integrations
- **GraalVM Polyglot 24.1.1** for JS/Python code execution
- **AWS SDK v2 2.25.60** for cloud services
- Custom security via `spring-boot-starter-security-config` (org.doindev)

## Architecture

### Node System (core pattern)

The central abstraction is the **node**: a unit of work in a workflow pipeline.

1. **`@Node` annotation** (`io.trellis.nodes.annotation.Node`) — marks a class as a workflow node. Includes metadata: `type` (unique ID), `displayName`, `category`, `version`, `trigger`/`polling` flags, `credentials` required.

2. **`NodeInterface`** (`io.trellis.nodes.core`) — contract every node implements. Key method: `execute(NodeExecutionContext) -> NodeExecutionResult`. Also defines `getParameters()`, `getInputs()`, `getOutputs()`, lifecycle hooks (`beforeExecute`/`afterExecute`), and `validateParameters()`.

3. **`NodeRegistry`** — Spring `@Component` that auto-discovers all `@Node`-annotated beans at startup via `ApplicationContext.getBeansWithAnnotation()`. Stores them in a `ConcurrentHashMap` keyed by `type` and `type_Vversion`. Supports versioned nodes with latest-version resolution.

4. **`NodeExecutionContext`** — carries execution state: IDs (execution, workflow, node), input data, parameters, credentials, static data, and `ExecutionMode` (MANUAL, TRIGGER, WEBHOOK, POLLING, INTERNAL).

5. **`NodeExecutionResult`** — output wrapper supporting single/multi-output, binary data, errors, hints, and persisted static data. Factory methods: `success()`, `successMultiOutput()`, `error()`, `empty()`.

### Data Format

All data flows through nodes as `List<Map<String, Object>>` wrapped in the standard format: `{ "json": <actual_data> }`. Access nested values via dot notation (e.g., `json.user.email`). Use `AbstractNode.wrapInJson()` / `unwrapJson()` helpers.

### Base Node Classes (`io.trellis.nodes.base`)

- **`AbstractNode`** — utility methods: nested value get/set (dot notation), JSON wrap/unwrap, deep clone, type conversions, error handling with `continueOnFail` support.
- **`AbstractApiNode`** — HTTP client support (RestClient, HttpClient), authentication (API Key, Basic, OAuth2), URL building, response parsing.
- **`AbstractDatabaseNode`** — JDBC connections, query execution, SQL builders, SQL type conversions.
- **`AbstractTriggerNode`** — trigger-only nodes (no inputs, timestamp injection).

### Creating a New Node

Annotate with `@Node`, implement `NodeInterface` (or extend an abstract base), and define parameters/inputs/outputs. The registry discovers it automatically:

```java
@Node(type = "myNode", displayName = "My Node", category = "Utility")
public class MyNode extends AbstractNode {
    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        // process context.getInputData(), return NodeExecutionResult.success(...)
    }
}
```

### Other Key Components

- **`config/`** — `AsyncExecutionConfig` (thread pool: 5 core, 20 max), `JacksonConfig`, `WebConfig`, `SpaForwardController` (Angular SPA routing).
- **`exception/`** — `GlobalExceptionHandler` with typed exceptions: `BadRequestException`, `NotFoundException`, `ForbiddenException`, `UnauthenticatedException`, `AuthException`, `ResponseException`, etc.
- **`util/`** — `NanoIdGenerator` (Hibernate ID strategy), `JsonObjectConverter`, `SecurityContextHelper`.

## Configuration

App runs on port **5678**. Key properties in `application.properties`:
- H2 console at `/h2-console` (dev only)
- `spring.jpa.hibernate.ddl-auto=update` (auto schema migration)
- CSRF disabled, CORS disabled, all endpoints `permitAll` (current dev config)
- Jackson: no-fail-on-unknown, non-null inclusion, ISO dates

## Package Structure

```
io.trellis/
├── TrellisApplication.java    # Entry point
├── config/                    # Spring configuration
├── exception/                 # Global exception handling
├── nodes/
│   ├── annotation/            # @Node annotation
│   ├── core/                  # NodeInterface, NodeRegistry, Context, Result, Parameter, Input, Output
│   └── base/                  # AbstractNode, AbstractApiNode, AbstractDatabaseNode, AbstractTriggerNode
└── util/                      # NanoId, JSON converter, security helper
```
