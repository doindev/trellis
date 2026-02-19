import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Project, ProjectMember } from '../models/project.model';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly basePath = '/projects';

  constructor(private api: ApiService) {}

  list(): Observable<Project[]> {
    return this.api.get<Project[]>(this.basePath);
  }

  get(id: string): Observable<Project> {
    return this.api.get<Project>(`${this.basePath}/${id}`);
  }

  create(data: Partial<Project>): Observable<Project> {
    return this.api.post<Project>(this.basePath, data);
  }

  update(id: string, data: Partial<Project>): Observable<Project> {
    return this.api.patch<Project>(`${this.basePath}/${id}`, data);
  }

  delete(id: string, transferToProjectId?: string): Observable<void> {
    if (transferToProjectId) {
      return this.api.post<void>(`${this.basePath}/${id}`, { transferToProjectId });
    }
    return this.api.delete<void>(`${this.basePath}/${id}`);
  }

  listMembers(projectId: string): Observable<ProjectMember[]> {
    return this.api.get<ProjectMember[]>(`${this.basePath}/${projectId}/members`);
  }

  addMember(projectId: string, userId: string, role: string): Observable<ProjectMember> {
    return this.api.post<ProjectMember>(`${this.basePath}/${projectId}/members`, { userId, role });
  }

  updateMember(projectId: string, userId: string, role: string): Observable<ProjectMember> {
    return this.api.patch<ProjectMember>(`${this.basePath}/${projectId}/members/${userId}`, { role });
  }

  removeMember(projectId: string, userId: string): Observable<void> {
    return this.api.delete<void>(`${this.basePath}/${projectId}/members/${userId}`);
  }
}
