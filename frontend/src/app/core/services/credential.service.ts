import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Credential, CredentialSchema, ModelInfo } from '../models';

@Injectable({ providedIn: 'root' })
export class CredentialService {
  private readonly path = '/credentials';

  constructor(private api: ApiService) {}

  list(): Observable<Credential[]> {
    return this.api.get<Credential[]>(this.path);
  }

  listByProject(projectId: string): Observable<Credential[]> {
    return this.api.get<Credential[]>(this.path, { projectId });
  }

  get(id: string): Observable<Credential> {
    return this.api.get<Credential>(`${this.path}/${id}`);
  }

  create(data: Partial<Credential>): Observable<Credential> {
    return this.api.post<Credential>(this.path, data);
  }

  update(id: string, data: Partial<Credential>): Observable<Credential> {
    return this.api.put<Credential>(`${this.path}/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${id}`);
  }

  getDecryptedData(id: string): Observable<Record<string, any>> {
    return this.api.get<Record<string, any>>(`${this.path}/${id}/data`);
  }

  getSchema(type: string): Observable<CredentialSchema> {
    return this.api.get<CredentialSchema>(`${this.path}/schema/${type}`);
  }

  listTypes(): Observable<CredentialSchema[]> {
    return this.api.get<CredentialSchema[]>(`${this.path}/types`);
  }

  listModels(credentialId: string, modelType?: string): Observable<ModelInfo[]> {
    const params: Record<string, string> = {};
    if (modelType) params['modelType'] = modelType;
    return this.api.get<ModelInfo[]>(`${this.path}/${credentialId}/models`, params);
  }

  testCredentials(type: string, data: Record<string, any>): Observable<{ success: boolean; error?: string }> {
    return this.api.post<{ success: boolean; error?: string }>(`${this.path}/test`, { type, data });
  }

  getShares(credentialId: string): Observable<string[]> {
    return this.api.get<string[]>(`${this.path}/${credentialId}/shares`);
  }

  shareCredential(credentialId: string, targetProjectId: string, callerProjectId: string): Observable<void> {
    return this.api.post<void>(`${this.path}/${credentialId}/shares`, { targetProjectId, callerProjectId });
  }

  unshareCredential(credentialId: string, targetProjectId: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${credentialId}/shares/${targetProjectId}`);
  }
}
