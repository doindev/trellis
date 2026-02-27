import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Tag } from '../models';

@Injectable({ providedIn: 'root' })
export class TagService {
  private readonly path = '/tags';

  constructor(private api: ApiService) {}

  list(): Observable<Tag[]> {
    return this.api.get<Tag[]>(this.path);
  }

  create(name: string): Observable<Tag> {
    return this.api.post<Tag>(this.path, { name });
  }

  update(id: string, name: string): Observable<Tag> {
    return this.api.put<Tag>(`${this.path}/${id}`, { name });
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${id}`);
  }

  updateWorkflowTags(workflowId: string, tagIds: string[]): Observable<any> {
    return this.api.put(`/workflows/${workflowId}/tags`, tagIds);
  }
}
