import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Workflow, WorkflowVersion } from '../models';

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly path = '/workflows';

  constructor(private api: ApiService) {}

  list(): Observable<Workflow[]> {
    return this.api.get<Workflow[]>(this.path);
  }

  get(id: string): Observable<Workflow> {
    return this.api.get<Workflow>(`${this.path}/${id}`);
  }

  create(data: Partial<Workflow>): Observable<Workflow> {
    return this.api.post<Workflow>(this.path, data);
  }

  update(id: string, data: Partial<Workflow>): Observable<Workflow> {
    return this.api.put<Workflow>(`${this.path}/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${id}`);
  }

  publish(id: string, request: { versionName?: string; description?: string }): Observable<Workflow> {
    return this.api.post<Workflow>(`${this.path}/${id}/publish`, request);
  }

  unpublish(id: string): Observable<Workflow> {
    return this.api.post<Workflow>(`${this.path}/${id}/unpublish`);
  }

  duplicate(id: string): Observable<Workflow> {
    return this.api.post<Workflow>(`${this.path}/${id}/duplicate`);
  }

  archive(id: string): Observable<Workflow> {
    return this.api.post<Workflow>(`${this.path}/${id}/archive`);
  }

  getVersions(id: string): Observable<WorkflowVersion[]> {
    return this.api.get<WorkflowVersion[]>(`${this.path}/${id}/versions`);
  }

  run(id: string, inputData?: any): Observable<any> {
    return this.api.post<any>(`${this.path}/${id}/run`, inputData || {});
  }

  stopExecution(executionId: string): Observable<any> {
    return this.api.post<any>(`/executions/${executionId}/stop`, {});
  }
}
