import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap, map, of } from 'rxjs';
import { ApiService } from './api.service';
import { NodeTypeDescription } from '../models';

@Injectable({ providedIn: 'root' })
export class NodeTypeService {
  private readonly path = '/node-types';
  private nodeTypesSubject = new BehaviorSubject<NodeTypeDescription[]>([]);
  private loaded = false;

  nodeTypes$ = this.nodeTypesSubject.asObservable();

  constructor(private api: ApiService) {}

  getAll(): Observable<NodeTypeDescription[]> {
    if (this.loaded) {
      return of(this.nodeTypesSubject.value);
    }
    return this.api.get<NodeTypeDescription[]>(this.path).pipe(
      tap(types => {
        this.nodeTypesSubject.next(types);
        this.loaded = true;
      })
    );
  }

  getByType(type: string): Observable<NodeTypeDescription> {
    return this.api.get<NodeTypeDescription>(`${this.path}/${type}`);
  }

  getCategories(): Observable<string[]> {
    return this.getAll().pipe(
      map(types => [...new Set(types.map(t => t.category))].sort())
    );
  }

  getCachedByType(type: string): NodeTypeDescription | undefined {
    return this.nodeTypesSubject.value.find(nt => nt.type === type);
  }

  getAgentOptions(projectId: string): Observable<{ value: string; name: string }[]> {
    return this.api.get<{ value: string; name: string }[]>(`${this.path}/agent-options`, { projectId });
  }
}
