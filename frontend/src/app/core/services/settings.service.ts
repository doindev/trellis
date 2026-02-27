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
  connectedAt: string;
  lastSeenAt: string;
}

export interface McpSettings {
  enabled: boolean;
  endpoints: McpEndpoint[];
}

export interface McpWorkflow {
  id: string;
  name: string;
  description: string;
  mcpEnabled: boolean;
  mcpDescription: string;
  published: boolean;
  hasWebhookNode: boolean;
  projectId?: string;
  projectName?: string;
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

  // MCP Settings
  getMcpSettings(): Observable<McpSettings> {
    return this.api.get<McpSettings>('/settings/mcp');
  }

  updateMcpSettings(settings: Partial<McpSettings>): Observable<McpSettings> {
    return this.api.put<McpSettings>('/settings/mcp', settings);
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

  // MCP Clients
  listMcpClients(): Observable<McpClient[]> {
    return this.api.get<McpClient[]>('/settings/mcp/clients');
  }
}
