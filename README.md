# CWC - Workflow Automation Platform

CWC is a workflow automation platform with a Java Spring Boot backend and Angular frontend. It features a node-based execution engine where workflow nodes are auto-discovered Spring components that process data through configurable pipelines.

## Build & Run

```bash
mvn clean install            # Full build (Java + Angular frontend)
mvn spring-boot:run          # Run the application
mvn test                     # Run tests
mvn clean compile            # Compile Java only (skip frontend)
```

The Angular frontend is built automatically via `frontend-maven-plugin` (Node v20.18.0).

---

## Application Properties Reference

All custom properties can be set in `src/main/resources/application.properties`, via environment variables, or JVM args (`-Dkey=value`).

### Server

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port the application listens on |

### Feature Toggles

Features are auto-detected from the classpath at startup. Use these properties to force-disable a feature even when its dependency is present. All default to `true` (auto-detect).

| Property | Default | Description |
|----------|---------|-------------|
| `cwc.features.langchain4j.enabled` | `true` | Enable AI / chat / agent nodes (requires LangChain4j on classpath) |
| `cwc.features.swagger.enabled` | `true` | Enable Swagger / OpenAPI UI (requires springdoc on classpath) |
| `cwc.features.mcp-server.enabled` | `true` | Enable MCP server endpoints (requires Spring AI MCP on classpath) |

Setting any of these to `false` will:
- Prevent the related beans, controllers, and services from loading
- Exclude the related node types from the node registry
- Hide the corresponding UI elements in the frontend

### Core Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `cwc.context-path` | `/` | Base context path for the application (e.g. `/cwc`) |
| `cwc.allow-non-owner-changes` | `false` | Allow non-owner users to make and deploy changes from the UI |
| `cwc.default-user-email` | `owner@cwc.local` | Default user email used when security is disabled |
| `cwc.support.email` | _(empty)_ | Support email address displayed in settings |

### Encryption

| Property | Default | Description |
|----------|---------|-------------|
| `cwc.encryption.key` | `change-me-in-production-32chars!` | AES-256 encryption key for credential storage. **Must be exactly 32 characters. Change in production.** |

### Webhooks

| Property | Default | Description |
|----------|---------|-------------|
| `cwc.webhook.base-path` | `/webhook/` | Base URL path for production webhook endpoints |
| `cwc.webhook.test-base-path` | `/webhook-test/` | Base URL path for test webhook endpoints |

### Config Bootstrap

The config bootstrap system loads workflows, credentials, variables, and settings from filesystem paths on startup.

| Property | Default | Description |
|----------|---------|-------------|
| `cwc.config.paths` | `.cwc` | Comma-separated filesystem paths to scan for config files |
| `cwc.config.mode` | `seed` | Bootstrap mode: `seed` (load once, skip existing) or `sync` (continuous sync) |
| `cwc.config.writeback` | `false` | Write settings changes back to the first config path. Only enable with proper backups. |

### Git Sync

| Property | Default | Description |
|----------|---------|-------------|
| `cwc.git.enabled` | `false` | Enable git-based configuration sync |
| `cwc.git.url` | _(empty)_ | Remote git repository URL |
| `cwc.git.branch` | `main` | Git branch to sync from |
| `cwc.git.token` | _(empty)_ | Authentication token for private repositories |
| `cwc.git.local-path` | `/opt/cwc/git-config` | Local directory for the cloned git repository |
| `cwc.git.sync-on-startup` | `true` | Pull from git automatically on application startup |
| `cwc.git.poll-interval` | `0` | Polling interval in seconds for periodic sync (0 = disabled) |
| `cwc.git.webhook-secret` | _(empty)_ | Secret for validating git webhook push events |

### AI / LLM

These properties configure the built-in chat assistant. Only relevant when `cwc.features.langchain4j.enabled` is `true`.

| Property | Default | Description |
|----------|---------|-------------|
| `cwc.ai.openai.api-key` | _(none)_ | OpenAI API key. The chat model bean is only created when this is set. |
| `cwc.ai.openai.model` | `gpt-4o-mini` | OpenAI model name for the default chat model |
| `cwc.chat.timeout-seconds` | `120` | Maximum seconds to wait for a single AI chat request |

### Security

| Property | Default | Description |
|----------|---------|-------------|
| `security.enabled` | `false` | Enable the security filter chain |
| `security.debug` | `true` | Enable security debug logging |
| `security.diagnostics.enabled` | `true` | Enable security diagnostics endpoint |

### Startup Profiles

Activate a profile with `spring.profiles.active=<profile>`:

| Profile | Config Paths | Writeback | Git Enabled | Description |
|---------|-------------|-----------|-------------|-------------|
| `local` | `.cwc` | `true` | `false` | Local development - filesystem only |
| `hybrid` | `.cwc` | `false` | `true` | Filesystem + git sync |
| `gitonly` | _(empty)_ | `false` | `true` | Git is the sole config source |
| `postgres` | _(unchanged)_ | _(unchanged)_ | _(unchanged)_ | Uses PostgreSQL instead of H2 |

### Placeholder Variables

These properties are referenced via `{{env:KEY}}` placeholders in config bootstrap files. They are resolved at bootstrap time from the Spring environment.

| Property | Default | Description |
|----------|---------|-------------|
| `fmp.api.key` | _(set in file)_ | Financial Modeling Prep API key |
| `massive.api.key` | _(set in file)_ | Massive API key |
| `finnhub.api.key` | _(set in file)_ | Finnhub API key |
| `fred.api.key` | _(set in file)_ | FRED (Federal Reserve Economic Data) API key |
| `postgresql.host` | `localhost` | PostgreSQL host for workflow credentials |
| `postgresql.port` | `5432` | PostgreSQL port for workflow credentials |
| `postgresql.database` | `postgres` | PostgreSQL database for workflow credentials |
| `postgresql.username` | `postgres` | PostgreSQL username for workflow credentials |
| `postgresql.password` | `postgres` | PostgreSQL password for workflow credentials |
| `postgresql.ssl` | `false` | Enable SSL for workflow PostgreSQL connections |

Any property in the Spring environment can be used as a placeholder. Auto-generated encrypted credential properties (prefixed with project names like `STOCK_INFRA_*`) are also resolved this way.
