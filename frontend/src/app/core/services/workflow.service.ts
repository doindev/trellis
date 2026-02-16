import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Workflow } from '../models';

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

  activate(id: string): Observable<Workflow> {
    return this.api.patch<Workflow>(`${this.path}/${id}/activate`);
  }

  deactivate(id: string): Observable<Workflow> {
    return this.api.patch<Workflow>(`${this.path}/${id}/deactivate`);
  }

  run(id: string, inputData?: any): Observable<any> {
    return this.api.post<any>(`${this.path}/${id}/run`, inputData || {});
  }
}
