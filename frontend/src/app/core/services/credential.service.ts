import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Credential, CredentialSchema } from '../models';

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

  getSchema(type: string): Observable<CredentialSchema> {
    return this.api.get<CredentialSchema>(`${this.path}/schema/${type}`);
  }

  listTypes(): Observable<CredentialSchema[]> {
    return this.api.get<CredentialSchema[]>(`${this.path}/types`);
  }
}
