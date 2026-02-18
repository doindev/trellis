import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { Execution } from '../models';

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class ExecutionService {
  private readonly path = '/executions';

  constructor(private api: ApiService) {}

  list(params?: Record<string, string>): Observable<Execution[]> {
    return this.api.get<Page<Execution>>(this.path, params).pipe(
      map(page => page.content)
    );
  }

  get(id: string): Observable<Execution> {
    return this.api.get<Execution>(`${this.path}/${id}`);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${id}`);
  }

  stop(id: string): Observable<Execution> {
    return this.api.post<Execution>(`${this.path}/${id}/stop`);
  }

  retry(id: string): Observable<Execution> {
    return this.api.post<Execution>(`${this.path}/${id}/retry`);
  }
}
