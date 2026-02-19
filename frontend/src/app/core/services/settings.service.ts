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
}
