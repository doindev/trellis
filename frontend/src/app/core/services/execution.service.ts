import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Execution } from '../models';

@Injectable({ providedIn: 'root' })
export class ExecutionService {
  private readonly path = '/executions';

  constructor(private api: ApiService) {}

  list(params?: Record<string, string>): Observable<Execution[]> {
    return this.api.get<Execution[]>(this.path, params);
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
