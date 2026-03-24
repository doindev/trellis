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

  getProjectMcp(projectId: string): Observable<ProjectMcpEndpoint[]> {
    return this.api.get<ProjectMcpEndpoint[]>(`${this.basePath}/${projectId}/mcp`);
  }

  updateProjectMcp(projectId: string, data: ProjectMcpRequest): Observable<ProjectMcpEndpoint> {
    return this.api.put<ProjectMcpEndpoint>(`${this.basePath}/${projectId}/mcp`, data);
  }

  /** Export a project as a JSON bundle (with workflows). */
  exportAsBundle(projectId: string): Observable<any> {
    return this.api.get<any>(`${this.basePath}/${projectId}/export`, { format: 'bundle' });
  }

  /** Export project settings only (no workflows). */
  exportSettingsOnly(projectId: string): Observable<any> {
    return this.api.get<any>(`${this.basePath}/${projectId}/export`, { format: 'settings' });
  }

  /** Download a project export as a ZIP file (triggers browser download). */
  exportAsZip(projectId: string): void {
    const baseUrl = ((window as any).__CWC_BASE_PATH__ || '') + '/api';
    const url = `${baseUrl}${this.basePath}/${projectId}/export?format=zip`;
    const a = document.createElement('a');
    a.href = url;
    a.download = 'project-export.zip';
    a.click();
  }

  /** Import a project from a file (ZIP or JSON). Returns import result. */
  importProject(file: File, mode: string = 'seed'): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('mode', mode);
    return this.api.postFormData<any>(`${this.basePath}/import`, formData);
  }
}

export interface ProjectMcpEndpoint {
  id?: string;
  name?: string;
  transport: string;
  path: string | null;
  url: string | null;
  enabled: boolean;
}

export interface ProjectMcpRequest {
  enabled: boolean;
  path?: string;
  transport: string;
}
