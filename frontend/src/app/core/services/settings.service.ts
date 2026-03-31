import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { ApiService } from './api.service';

export interface UsageStats {
  workflows: number;
  executions: number;
  executionsSuccess: number;
  executionsError: number;
  credentials: number;
  users: number;
  activeWebhooks: number;
}

export interface UserInfo {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApiKeyInfo {
  id: string;
  label: string;
  keyPrefix: string;
  userId: string;
  createdAt: string;
  expiresAt: string;
  apiKey?: string; // only returned on creation
}

export interface AiSettings {
  provider: string;
  apiKey: string;
  model: string;
  baseUrl: string | null;
  enabled: boolean;
}

export interface AiModelInfo {
  id: string;
  name: string;
}

export interface McpEndpoint {
  id: string;
  name: string;
  transport: string;
  path: string;
  url: string;
  enabled: boolean;
}

export interface McpClient {
  sessionId: string;
  endpointId: string;
  endpointName: string;
  transport: string;
  clientName: string;
  clientVersion: string;
  projectId: string | null;
  connectedAt: string;
  lastSeenAt: string;
  disconnectedAt: string | null;
}

export interface McpServer {
  id: string;
  name: string;
  projectId: string | null;
  endpoints: McpEndpoint[];
  connectedClients: number;
}

export interface McpSettings {
  enabled: boolean;
  agentToolsEnabled: boolean;
  agentToolsDedicated: boolean;
  agentToolsPath: string;
  agentToolsTransport: string;
  agentToolsUrl: string;
  endpoints: McpEndpoint[];
}

export type McpParamType = 'string' | 'number' | 'integer' | 'boolean' | 'object' | 'array';

export interface McpParameter {
  name: string;
  type: McpParamType | McpParamType[];
  description: string;
  required: boolean;
  // Nesting support
  properties?: McpParameter[];       // For type: 'object'
  items?: { type: McpParamType };    // For type: 'array'
  // Constraints
  enum?: string[];
  minimum?: number;
  maximum?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  default?: any;
}

export interface McpOutputProperty {
  name: string;
  type: McpParamType;
  description: string;
}

/** Raw JSON Schema object — used when storing schema directly from Code mode */
export interface JsonSchemaObject {
  type: 'object';
  properties?: Record<string, any>;
  required?: string[];
  [key: string]: any;
}

export interface McpOutputSchema {
  format: 'json' | 'text' | 'html' | 'xml';
  description?: string;
  properties?: McpParameter[] | McpOutputProperty[];
}

export interface McpWorkflow {
  id: string;
  name: string;
  type: string;
  description: string;
  mcpEnabled: boolean;
  mcpDescription: string;
  mcpInputSchema: McpParameter[] | JsonSchemaObject | null;
  mcpOutputSchema: McpOutputSchema | null;
  published: boolean;
  hasWebhookNode: boolean;
  projectId?: string;
  projectName?: string;
}

export interface ExecutionSettings {
  saveExecutionProgress: string;   // 'yes' | 'no'
  saveManualExecutions: string;    // 'yes' | 'no'
  executionTimeout: number;        // seconds, -1 = disabled
  errorWorkflow: string | null;
}

export interface SwaggerSettings {
  enabled: boolean;
  apiTitle: string;
  apiDescription: string;
  apiVersion: string;
}

export interface SwaggerWorkflow {
  id: string;
  name: string;
  description: string;
  swaggerEnabled: boolean;
  published: boolean;
  hasWebhookNode: boolean;
  projectId?: string;
  projectName?: string;
}

export interface ResolvedSettingValue {
  value: any;
  source: 'workflow' | 'project' | 'application';
}

export interface ResolvedExecutionSettings {
  saveExecutionProgress: ResolvedSettingValue;
  saveManualExecutions: ResolvedSettingValue;
  executionTimeout: ResolvedSettingValue;
  errorWorkflow: ResolvedSettingValue;
}

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private settings$?: Observable<any>;

  constructor(private api: ApiService) {}

  getSettings(): Observable<any> {
    if (!this.settings$) {
      this.settings$ = this.api.get<any>('/settings').pipe(
        shareReplay(1)
      );
    }
    return this.settings$;
  }

  getUsage(): Observable<UsageStats> {
    return this.api.get<UsageStats>('/settings/usage');
  }

  // Users
  listUsers(): Observable<UserInfo[]> {
    return this.api.get<UserInfo[]>('/users');
  }

  createUser(data: { email: string; firstName?: string; lastName?: string; password: string; role?: string }): Observable<UserInfo> {
    return this.api.post<UserInfo>('/users', data);
  }

  updateUser(id: string, data: Partial<UserInfo & { password?: string }>): Observable<UserInfo> {
    return this.api.put<UserInfo>(`/users/${id}`, data);
  }

  deleteUser(id: string): Observable<void> {
    return this.api.delete<void>(`/users/${id}`);
  }

  // API Keys
  listApiKeys(): Observable<ApiKeyInfo[]> {
    return this.api.get<ApiKeyInfo[]>('/api-keys');
  }

  createApiKey(label: string): Observable<ApiKeyInfo> {
    return this.api.post<ApiKeyInfo>('/api-keys', { label });
  }

  deleteApiKey(id: string): Observable<void> {
    return this.api.delete<void>(`/api-keys/${id}`);
  }

  // AI Settings
  getAiSettings(): Observable<AiSettings> {
    return this.api.get<AiSettings>('/settings/ai');
  }

  updateAiSettings(settings: Partial<AiSettings>): Observable<AiSettings> {
    return this.api.put<AiSettings>('/settings/ai', settings);
  }

  listAiModels(provider: string, apiKey: string, baseUrl?: string | null): Observable<AiModelInfo[]> {
    const body: any = { provider, apiKey };
    if (baseUrl) body.baseUrl = baseUrl;
    return this.api.post<AiModelInfo[]>('/settings/ai/models', body);
  }

  // MCP Settings
  getMcpSettings(): Observable<McpSettings> {
    return this.api.get<McpSettings>('/settings/mcp');
  }

  updateMcpSettings(settings: Partial<McpSettings>): Observable<McpSettings> {
    return this.api.put<McpSettings>('/settings/mcp', settings);
  }

  updateAgentToolsSettings(data: { agentToolsEnabled?: boolean; agentToolsDedicated?: boolean; agentToolsPath?: string; agentToolsTransport?: string }): Observable<McpSettings> {
    return this.api.put<McpSettings>('/settings/mcp/agent-tools', data);
  }

  getMcpWorkflows(): Observable<McpWorkflow[]> {
    return this.api.get<McpWorkflow[]>('/settings/mcp/workflows');
  }

  updateMcpWorkflow(workflowId: string, data: Partial<McpWorkflow>): Observable<void> {
    return this.api.put<void>(`/settings/mcp/workflows/${workflowId}`, data);
  }

  revokeMcpWorkflow(workflowId: string): Observable<void> {
    return this.api.delete<void>(`/settings/mcp/workflows/${workflowId}`);
  }

  autoDetectMcpParams(workflowId: string): Observable<McpParameter[]> {
    return this.api.get<McpParameter[]>(`/settings/mcp/workflows/${workflowId}/auto-detect-params`);
  }

  // MCP Endpoints
  listMcpEndpoints(): Observable<McpEndpoint[]> {
    return this.api.get<McpEndpoint[]>('/settings/mcp/endpoints');
  }

  createMcpEndpoint(endpoint: Partial<McpEndpoint>): Observable<McpEndpoint> {
    return this.api.post<McpEndpoint>('/settings/mcp/endpoints', endpoint);
  }

  updateMcpEndpoint(id: string, endpoint: Partial<McpEndpoint>): Observable<McpEndpoint> {
    return this.api.put<McpEndpoint>(`/settings/mcp/endpoints/${id}`, endpoint);
  }

  deleteMcpEndpoint(id: string): Observable<void> {
    return this.api.delete<void>(`/settings/mcp/endpoints/${id}`);
  }

  // MCP Servers
  getMcpServers(): Observable<McpServer[]> {
    return this.api.get<McpServer[]>('/settings/mcp/servers');
  }

  // MCP Clients
  listMcpClients(): Observable<McpClient[]> {
    return this.api.get<McpClient[]>('/settings/mcp/clients');
  }

  // Swagger Settings
  getSwaggerSettings(): Observable<SwaggerSettings> {
    return this.api.get<SwaggerSettings>('/settings/swagger');
  }

  updateSwaggerSettings(settings: Partial<SwaggerSettings>): Observable<SwaggerSettings> {
    return this.api.put<SwaggerSettings>('/settings/swagger', settings);
  }

  getSwaggerWorkflows(): Observable<SwaggerWorkflow[]> {
    return this.api.get<SwaggerWorkflow[]>('/settings/swagger/workflows');
  }

  updateSwaggerWorkflow(workflowId: string, data: Partial<SwaggerWorkflow>): Observable<void> {
    return this.api.put<void>(`/settings/swagger/workflows/${workflowId}`, data);
  }

  // Execution Settings
  getExecutionSettings(): Observable<ExecutionSettings> {
    return this.api.get<ExecutionSettings>('/settings/execution');
  }

  updateExecutionSettings(settings: Partial<ExecutionSettings>): Observable<ExecutionSettings> {
    return this.api.put<ExecutionSettings>('/settings/execution', settings);
  }

  resolveExecutionSettings(projectId: string | null, workflowSettings: Record<string, any> | null): Observable<ResolvedExecutionSettings> {
    return this.api.post<ResolvedExecutionSettings>('/settings/execution/resolve', {
      projectId,
      workflowSettings
    });
  }

  // --- Environments / Source Control (instance-level) ---

  getSourceControlSettings(): Observable<any> {
    return this.api.get<any>('/settings/environments/source-control');
  }

  updateSourceControlSettings(data: { provider: string; repoUrl: string; branch: string; token: string; enabled: boolean }): Observable<any> {
    return this.api.put<any>('/settings/environments/source-control', data);
  }

  syncSourceControl(): Observable<any> {
    return this.api.post<any>('/settings/environments/sync', {});
  }
}
