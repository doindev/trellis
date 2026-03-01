import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

export interface CacheDefinition {
  id: string;
  name: string;
  description?: string;
  maxSize: number;
  ttlSeconds: number;
  estimatedSize?: number;
  hitCount?: number;
  missCount?: number;
  hitRate?: number;
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({ providedIn: 'root' })
export class CacheService {
  private readonly path = '/caches';

  constructor(private api: ApiService) {}

  list(): Observable<CacheDefinition[]> {
    return this.api.get<CacheDefinition[]>(this.path);
  }

  get(id: string): Observable<CacheDefinition> {
    return this.api.get<CacheDefinition>(`${this.path}/${id}`);
  }

  create(data: Partial<CacheDefinition>): Observable<CacheDefinition> {
    return this.api.post<CacheDefinition>(this.path, data);
  }

  update(id: string, data: Partial<CacheDefinition>): Observable<CacheDefinition> {
    return this.api.put<CacheDefinition>(`${this.path}/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${id}`);
  }

  clear(id: string): Observable<CacheDefinition> {
    return this.api.post<CacheDefinition>(`${this.path}/${id}/clear`, {});
  }

  getStats(id: string): Observable<any> {
    return this.api.get<any>(`${this.path}/${id}/stats`);
  }
}
