import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Workflow, WorkflowVersion, WorkflowShare, Page } from '../models';

@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private readonly path = '/workflows';

  constructor(private api: ApiService) {}

  list(params?: Record<string, string>): Observable<Workflow[]> {
    return this.api.get<Workflow[]>(this.path, params);
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

  publish(id: string, request: { versionName?: string; description?: string; includePinData?: boolean }): Observable<Workflow> {
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

  getVersions(id: string, page = 0, size = 20, filter = 'all'): Observable<Page<WorkflowVersion>> {
    return this.api.get<Page<WorkflowVersion>>(`${this.path}/${id}/versions`, {
      page: String(page),
      size: String(size),
      filter
    });
  }

  getVersion(workflowId: string, versionId: string): Observable<WorkflowVersion> {
    return this.api.get<WorkflowVersion>(`${this.path}/${workflowId}/versions/${versionId}`);
  }

  publishFromVersion(workflowId: string, versionId: string): Observable<Workflow> {
    return this.api.post<Workflow>(`${this.path}/${workflowId}/versions/${versionId}/publish`);
  }

  cloneFromVersion(workflowId: string, versionId: string): Observable<Workflow> {
    return this.api.post<Workflow>(`${this.path}/${workflowId}/versions/${versionId}/clone`);
  }

  move(id: string, projectId: string): Observable<Workflow> {
    return this.api.post<Workflow>(`${this.path}/${id}/move`, { projectId });
  }

  getShares(id: string): Observable<WorkflowShare[]> {
    return this.api.get<WorkflowShare[]>(`${this.path}/${id}/shares`);
  }

  addShare(id: string, userId: string, permission: string): Observable<WorkflowShare> {
    return this.api.post<WorkflowShare>(`${this.path}/${id}/shares`, { userId, permission });
  }

  updateShare(workflowId: string, shareId: string, permission: string): Observable<WorkflowShare> {
    return this.api.patch<WorkflowShare>(`${this.path}/${workflowId}/shares/${shareId}`, { permission });
  }

  removeShare(workflowId: string, shareId: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${workflowId}/shares/${shareId}`);
  }

  run(id: string, inputData?: any): Observable<any> {
    return this.api.post<any>(`${this.path}/${id}/run`, inputData || {});
  }

  executeNode(request: {
    nodeType: string;
    typeVersion: number;
    parameters: Record<string, any>;
    credentials?: Record<string, any>;
    inputData: any[];
    workflowId: string;
    nodeId: string;
  }): Observable<{ output: any[][]; error?: string }> {
    return this.api.post('/nodes/execute', request);
  }

  evaluateExpression(expression: string, inputData: any[], nodeOutputs?: Record<string, any>): Observable<{ result: any; error: string }> {
    const body: any = { expression, inputData };
    if (nodeOutputs) body.nodeOutputs = nodeOutputs;
    return this.api.post<{ result: any; error: string }>('/expressions/evaluate', body);
  }

  startExecution(executionId: string, triggerNodeId?: string): Observable<any> {
    const body: any = {};
    if (triggerNodeId) { body.triggerNodeId = triggerNodeId; }
    return this.api.post<any>(`/executions/${executionId}/start`, body);
  }

  stopExecution(executionId: string): Observable<any> {
    return this.api.post<any>(`/executions/${executionId}/stop`, {});
  }
}
