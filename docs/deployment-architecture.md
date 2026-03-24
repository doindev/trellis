# CWC Deployment & Configuration Architecture

This document describes the architecture for file-based project/workflow deployment, GitHub repository integration, environment management, and Kubernetes multi-instance operation.

---

## Table of Contents

1. [Properties Reference](#1-properties-reference)
2. [File-Based Bootstrap](#2-file-based-bootstrap)
3. [Config File Schemas](#3-config-file-schemas)
4. [Placeholder Syntax](#4-placeholder-syntax)
5. [Import / Export API](#5-import--export-api)
6. [Identity Resolution & Seed/Sync Modes](#6-identity-resolution--seedsync-modes)
7. [GitHub Repository Integration](#7-github-repository-integration)
8. [Environment Management](#8-environment-management)
9. [Kubernetes Deployment](#9-kubernetes-deployment)
10. [CI/CD Patterns](#10-cicd-patterns)
11. [Startup Chain](#11-startup-chain)
12. [Entity Changes](#12-entity-changes)

---

## 1. Properties Reference

### Config Bootstrap

```properties
# Comma-separated config paths (empty = disabled). Later paths override earlier.
cwc.config.paths=

# seed = only create entities that don't exist in DB
# sync = always upsert from files (file is desired state)
cwc.config.mode=seed
```

### Git Sync

```properties
cwc.git.enabled=false
cwc.git.url=
cwc.git.branch=main
cwc.git.token=
cwc.git.local-path=/opt/cwc/git-config
cwc.git.sync-on-startup=true
cwc.git.poll-interval=0                       # seconds, 0 = disabled
cwc.git.webhook-secret=

# Per-project repos (optional)
cwc.git.projects.<configId>.url=
cwc.git.projects.<configId>.branch=main
cwc.git.projects.<configId>.token=
```

### Actuator / Health Probes

```properties
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.probes.enabled=true
management.health.livenessState.enabled=true
management.health.readinessState.enabled=true
```

---

## 2. File-Based Bootstrap

### Discovery Modes

Each config path independently determines its discovery mode:

- **Manifest mode**: If `manifest.json` exists at the path root, only files listed in the manifest are processed.
- **Convention mode**: If no `manifest.json`, scan for `settings.json` at root and `projects/*/project.json` directories with `workflows/*.json` subdirectories.

If both `manifest.json` and a `projects/` directory exist, **manifest wins** with a logged warning.

### Convention Directory Structure

```
<config-path>/
├── settings.json                        # Global application settings
├── projects/
│   ├── order-processing/
│   │   ├── project.json                 # Project definition
│   │   └── workflows/
│   │       ├── new-order-webhook.json
│   │       └── daily-report.json
│   └── customer-support/
│       ├── project.json
│       └── workflows/
│           └── ticket-router.json
```

### Manifest Format

```json
{
  "version": "1.0",
  "settings": "settings.json",
  "projects": [
    {
      "config": "projects/order-processing/project.json",
      "workflows": [
        "projects/order-processing/workflows/new-order-webhook.json"
      ]
    }
  ]
}
```

All paths are relative to `manifest.json`'s parent directory. Missing files are logged as errors but don't fail startup.

### Multi-Path Precedence

Paths in `cwc.config.paths` are processed left to right. Later paths override earlier:

| Entity type         | Identity key        | Override behavior                              |
|---------------------|---------------------|------------------------------------------------|
| Global settings     | Singleton           | Deep-merge (later values override field-by-field) |
| Projects            | `configId`          | Later replaces entire project definition       |
| Workflows           | `configId`          | Later replaces entire workflow definition      |
| Variables           | `key`               | Later value replaces earlier for same key      |
| Credentials         | `ref`               | Later replaces earlier for same ref name       |
| Caches              | `name`              | Later replaces earlier for same name           |

---

## 3. Config File Schemas

### settings.json

```json
{
  "ai": {
    "provider": "openai",
    "model": "gpt-4o",
    "baseUrl": null,
    "apiKey": "{{env:CWC_AI_API_KEY}}",
    "enabled": true
  },
  "execution": {
    "saveExecutionProgress": "yes",
    "saveManualExecutions": "yes",
    "executionTimeout": 300,
    "errorWorkflow": null
  },
  "mcp": {
    "enabled": true,
    "agentToolsEnabled": true,
    "agentToolsDedicated": true,
    "agentToolsPath": "agent",
    "agentToolsTransport": "STREAMABLE_HTTP",
    "endpoints": [
      {
        "name": "Shared MCP Server",
        "transport": "STREAMABLE_HTTP",
        "path": "{{env:MCP_SERVER_URL}}",
        "enabled": true
      }
    ]
  },
  "swagger": {
    "enabled": false,
    "apiTitle": "CWC API",
    "apiDescription": "Workflow automation API",
    "apiVersion": "1.0.0"
  },
  "gitRepos": [
    {
      "configId": "order-processing",
      "url": "https://github.com/team-a/cwc-orders.git",
      "branch": "main",
      "token": "{{env:TEAM_A_GIT_TOKEN}}"
    }
  ],
  "environments": [
    {
      "name": "development",
      "branch": "develop",
      "description": "Development environment"
    },
    {
      "name": "staging",
      "branch": "staging",
      "description": "Pre-production testing"
    },
    {
      "name": "production",
      "branch": "main",
      "description": "Live environment"
    }
  ]
}
```

### project.json

```json
{
  "configId": "order-processing",
  "name": "Order Processing",
  "type": "TEAM",
  "contextPath": "orders",
  "description": "Handles order intake and routing",
  "settings": {
    "execution": {
      "saveExecutionProgress": "yes",
      "saveManualExecutions": "no",
      "executionTimeout": 120
    }
  },
  "mcp": {
    "enabled": true,
    "path": "orders",
    "transport": "STREAMABLE_HTTP",
    "endpoints": [
      {
        "name": "Order Service MCP",
        "transport": "STREAMABLE_HTTP",
        "path": "{{env:ORDER_MCP_URL}}",
        "enabled": true
      }
    ]
  },
  "variables": [
    { "key": "API_BASE_URL", "value": "{{env:ORDER_API_URL:https://api.example.com}}" },
    { "key": "DEBUG_MODE", "value": "false", "type": "string" }
  ],
  "credentials": [
    {
      "ref": "slack-bot",
      "name": "Slack Bot",
      "type": "slackApi",
      "data": {
        "accessToken": "{{env:ORDER_SLACK_TOKEN}}"
      }
    },
    {
      "ref": "order-db",
      "name": "Order Database",
      "type": "postgres",
      "data": {
        "host": "{{env:ORDER_DB_HOST:localhost}}",
        "port": "{{env:ORDER_DB_PORT:5432}}",
        "database": "{{env:ORDER_DB_NAME:orders}}",
        "user": "{{env:ORDER_DB_USER}}",
        "password": "{{env:ORDER_DB_PASSWORD}}"
      }
    }
  ],
  "caches": [
    { "name": "api-cache", "maxSize": 1000, "ttlSeconds": 300 }
  ],
  "tags": ["production", "orders"]
}
```

### Workflow JSON

Extends the existing frontend export format with two additions:

```json
{
  "configId": "new-order-webhook",
  "name": "New Order Webhook",
  "description": "Receives and processes new orders",
  "type": "WORKFLOW",
  "published": true,
  "nodes": [
    {
      "id": "webhook1",
      "name": "Order Webhook",
      "type": "webhook",
      "parameters": { "path": "/orders/new", "httpMethod": "POST" },
      "position": [250, 300],
      "credentials": {
        "slackApi": { "ref": "slack-bot" }
      }
    }
  ],
  "connections": {},
  "settings": {}
}
```

- `configId` is optional (derived from filename if missing).
- Credentials use `"ref"` instead of `"id"` for file-based workflows.
- `published: true` auto-publishes the workflow after creation.

---

## 4. Placeholder Syntax

### Format

| Pattern                    | Resolves to                          |
|----------------------------|--------------------------------------|
| `{{env:VAR_NAME}}`        | Value of env var / Spring property   |
| `{{env:VAR_NAME:default}}`| Value, or default if unset           |
| `{{env:VAR_NAME:}}`       | Value, or empty string if unset      |

Multiple placeholders in one string are supported:
`"https://{{env:API_HOST}}:{{env:API_PORT:8080}}/v1"`

### Resolution

1. JSON file is parsed into an object tree first (Jackson).
2. Recursive tree walk finds all `String` values containing `{{env:...}}`.
3. Each placeholder resolves via `Spring Environment.getProperty()`.
4. Unresolvable placeholders without defaults are logged as errors and left as-is.

### Scope

Placeholders are resolved in `settings.json` and `project.json` only. Workflow JSON files are not processed for placeholders — they stay environment-agnostic. Credentials flow via `ref`, variables via the `$vars` expression system at runtime.

---

## 5. Import / Export API

### Import Endpoints

```
POST /api/projects/import              # Full bundle (ZIP or single-JSON)
POST /api/projects/{id}/import         # Merge into existing project
POST /api/workflows/import             # Single workflow import
```

Format auto-detection:
- `application/zip` or `.zip` extension -> ZIP mode
- `application/json` or `.json` -> check for `"project"` key -> bundle mode, else bare project.json

### Export Endpoints

```
GET /api/projects/{id}/export                # Default: ZIP
GET /api/projects/{id}/export?format=zip     # ZIP archive
GET /api/projects/{id}/export?format=bundle  # Single JSON with embedded workflows
```

### Export Behavior

- Credentials export with auto-generated `{{env:...}}` placeholder templates
- Placeholder names: `{{env:PROJECT_CREDREF_FIELDNAME}}` (upper-snake)
- If a credential/variable was imported with a specific placeholder, the original placeholder is preserved (via `sourcePlaceholder` column)
- Workflow credential references export as `"ref"` instead of `"id"`
- Workflows without a `configId` get one auto-generated from their name

### Reload Endpoint

```
POST /api/admin/reload-config
POST /api/admin/reload-config?mode=sync
POST /api/admin/reload-config?adoptOrphans=true
```

Returns `ConfigReloadResult` with counts of created/updated/skipped/failed entities, errors, and warnings.

---

## 6. Identity Resolution & Seed/Sync Modes

### configId

Stable identity key for matching file-based entities to DB entities. Added as a nullable column on `ProjectEntity`, `WorkflowEntity`, `CredentialEntity`, and `CacheDefinitionEntity`.

- Optional in files — derived from filename if missing (e.g., `new-order-webhook.json` -> `new-order-webhook`).
- Slug format: `[a-z0-9-]+`.
- Workflow configId is unique within a project.
- UI-created entities have `configId = null` and are never matched by the bootstrap.

### Seed vs Sync Mode

| Action                                   | seed mode      | sync mode       |
|------------------------------------------|----------------|-----------------|
| Entity with configId not in DB           | Create         | Create          |
| Entity with configId already in DB       | **Skip**       | **Update**      |
| Workflow deleted via UI, still in file   | Not recreated  | **Recreated**   |
| Workflow added via UI, not in file       | Left alone     | Left alone      |
| Variable with same key exists            | **Skip**       | **Update**      |

Sync mode does NOT delete entities missing from files. It only creates or updates.

### Credential Resolution in Workflows (Hybrid)

1. If credential reference has `"ref"` -> look up in project's ref-to-dbId map.
2. If it has `"id"` + `"name"` (UI export format) -> try name+type matching as fallback.
3. If both fail -> log warning, save workflow as-is.

### Collision Handling

If a file project's `contextPath` matches an existing UI-created project (no configId), startup logs an actionable error. Use `?adoptOrphans=true` on the reload endpoint to auto-adopt by contextPath/name match.

### Failure Policy

The bootstrap **never crashes startup**. Errors are logged and reported via:
- Startup log summary
- `ConfigReloadResult` (from reload API)
- `ConfigBootstrapHealthIndicator` (readiness probe)

Transactions are per-project atomic (credentials + variables + caches together), per-workflow separate.

---

## 7. GitHub Repository Integration

### Architecture

The app does not require git. Config files just need to exist at `cwc.config.paths`. Delivery mechanisms:

1. **Built-in git pull** (ProcessBuilder-based) — if `cwc.git.url` is configured
2. **K8s git-sync sidecar** — external container syncs to a shared volume
3. **CI/CD pipeline** — copies files to ConfigMap or volume
4. **Manual** — developer places files directly

### Multi-Repo Support

- **Root repo**: Application settings + optionally some projects
- **Per-project repos**: Declared in properties (`cwc.git.projects.<configId>.*`) or in root `settings.json` `gitRepos` array

Root repo is cloned first. Per-project repos are cloned into subdirectories.

### Git Push & PR Creation

```
POST /api/projects/{id}/promote
{
  "targetEnvironment": "staging",
  "commitMessage": "Update order processing workflows"
}
```

Backend: exports project to files -> creates branch `cwc/update-<configId>-<timestamp>` -> commits -> pushes -> creates PR via GitHub/GitLab REST API -> returns PR URL.

### Config Storage

- **Properties** provide git config at startup (before DB is populated)
- **DB entity** (`SourceControlSettingsEntity`) stores runtime config from the Settings UI
- DB takes precedence over properties when present

### Sync Triggers

1. **Startup** — always syncs if configured
2. **Manual reload** — `POST /api/admin/reload-config`
3. **Periodic polling** — if `cwc.git.poll-interval > 0`
4. **Webhook** — `POST /api/admin/git-webhook` (secured with `cwc.git.webhook-secret`)

---

## 8. Environment Management

### Concept

Environments are lightweight labels on git branches. Each CWC instance belongs to one environment (determined by matching `cwc.git.branch` to environment definitions).

No cross-instance deployment tracking in v1. Each instance is aware of all environments but only manages its own.

### Entity

```
EnvironmentEntity:
  id, name, gitBranch, description, sortOrder, isCurrent
```

### Promotion

Git-based: export -> branch -> PR -> merge -> target instance syncs.

The workflow editor toolbar "Push to Git" becomes a dropdown:
- "Push to development (current)"
- "Promote to staging" (creates PR: develop -> staging)
- "Promote to production" (creates PR: develop -> main)

### Settings UI

The "Environments" stub in Settings becomes functional:
- Git connection form (repo URL, branch, token, test connection)
- Environment list (name, branch, description, current indicator)
- Sync status (last sync time, status, manual "Sync now" button)

---

## 9. Kubernetes Deployment

### Pod Architecture

Homogeneous pods — every pod runs the full application. `TriggerLockService` deduplicates trigger execution across pods. `ClusterSyncService` propagates config changes within 10 seconds.

### Graceful Shutdown

1. `TriggerLockService.@PreDestroy` — releases all trigger locks immediately
2. `WorkflowEngine.@PreDestroy` — waits up to 25s for in-flight executions, then marks remaining as ERROR
3. K8s `terminationGracePeriodSeconds: 30`
4. Optional `preStop` sleep for LB drain

### Execution Recovery

On startup, `ExecutionRecoveryInitializer` scans for orphaned executions:
- `RUNNING` executions from dead instances -> marked as `ERROR` with "pod terminated" message
- `WAITING` executions -> left alone (WaitPollerService resumes them naturally)

Instance identification via `instanceId` column on `ExecutionEntity`.

### Health Probes

| Probe     | Path                            | Checks                                    |
|-----------|---------------------------------|-------------------------------------------|
| Liveness  | `/actuator/health/liveness`     | App is alive, not deadlocked              |
| Readiness | `/actuator/health/readiness`    | DB connected, bootstrap complete, triggers registered |
| Startup   | `/actuator/health/liveness`     | App finished starting (150s max)          |

Custom `CwcReadinessIndicator` gates traffic until initialization is complete.
`ConfigBootstrapHealthIndicator` reports config errors without failing the pod.

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache git curl
WORKDIR /app
COPY target/*.jar app.jar
RUN mkdir -p /opt/cwc/config
VOLUME /opt/cwc/config
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Helm Chart

Located at `helm/cwc/` with templates for Deployment, Service, ConfigMap, Secret, Ingress, and HPA.

Key `values.yaml` settings: `replicaCount`, `config.mode`, `config.paths`, `config.git.*`, `database.*`, `encryptionKey.*`, `autoscaling.*`.

---

## 10. CI/CD Patterns

Three supported deployment patterns (all work without recompilation):

### Pattern A: API-Driven

```bash
zip -r config.zip config/
curl -X POST "$CWC_URL/api/projects/import" \
  -H "Authorization: Bearer $API_KEY" \
  -F "file=@config.zip" -F "mode=sync"
```

### Pattern B: Git-Sync + Reload

```bash
git push origin main
curl -X POST "$CWC_URL/api/admin/reload-config?mode=sync" \
  -H "Authorization: Bearer $API_KEY"
```

### Pattern C: ConfigMap + Rolling Restart

```bash
kubectl create configmap cwc-config --from-file=config/ \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl rollout restart deployment/cwc
```

---

## 11. Startup Chain

```
1. DataSeeder                @Order(1)  CommandLineRunner
   └─ Creates default user + personal project

2. GitSyncRunner             @Order(2)  CommandLineRunner
   └─ Git clone/pull if cwc.git.enabled=true

3. ConfigBootstrapRunner     @Order(3)  CommandLineRunner
   └─ Reads cwc.config.paths
   └─ Discovers files (convention or manifest per path)
   └─ Merges across paths (later wins)
   └─ Resolves {{env:...}} placeholders
   └─ Applies to DB (seed or sync mode)
   └─ Resolves credential refs in workflows
   └─ Triggers downstream refreshes (webhooks, context paths)

4. ExecutionRecoveryInitializer         ApplicationReadyEvent @Order(1)
   └─ Scans for orphaned RUNNING executions from dead instances
   └─ Marks as ERROR

5. TriggerStartupInitializer            ApplicationReadyEvent @Order(2)
   └─ Registers triggers for all published workflows
```

---

## 12. Entity Changes

| Entity                  | New Column           | Type    | Notes                                      |
|-------------------------|----------------------|---------|--------------------------------------------|
| `ProjectEntity`         | `configId`           | String  | Unique, nullable                           |
| `WorkflowEntity`        | `configId`           | String  | Nullable, unique within project            |
| `CredentialEntity`      | `configId`           | String  | Nullable, matches `ref` from project.json  |
| `CredentialEntity`      | `sourcePlaceholder`  | String  | Nullable, tracks original `{{env:...}}`    |
| `VariableEntity`        | `sourcePlaceholder`  | String  | Nullable, tracks original `{{env:...}}`    |
| `CacheDefinitionEntity` | `configId`           | String  | Nullable                                   |
| `ExecutionEntity`       | `instanceId`         | String  | Nullable, tracks which pod started it      |

### New Entities

| Entity                          | Purpose                                             |
|---------------------------------|-----------------------------------------------------|
| `SourceControlSettingsEntity`   | Git connection config (URL, branch, token, status)  |
| `ProjectGitConfigEntity`        | Per-project git repo config                         |
| `EnvironmentEntity`             | Named environments (name, branch, description)      |
