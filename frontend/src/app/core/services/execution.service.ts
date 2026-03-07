import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiService } from './api.service';
import { Execution } from '../models';

export interface ExecutionPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface MetricsBucket {
  time: string;
  total: number;
  success: number;
  error: number;
  canceled: number;
  totalDurationMs: number;
  finishedCount: number;
}

export interface MetricsSummary {
  total: number;
  success: number;
  error: number;
  canceled: number;
  totalDurationMs: number;
  finishedCount: number;
}

export interface MetricsResponse {
  summary: MetricsSummary;
  buckets: MetricsBucket[];
}

@Injectable({ providedIn: 'root' })
export class ExecutionService {
  private readonly path = '/executions';

  constructor(private api: ApiService) {}

  list(params?: Record<string, string>): Observable<Execution[]> {
    return this.api.get<ExecutionPage<Execution>>(this.path, params).pipe(
      map(page => page.content)
    );
  }

  listPaged(params?: Record<string, string>): Observable<ExecutionPage<Execution>> {
    return this.api.get<ExecutionPage<Execution>>(this.path, params);
  }

  getMetrics(params: Record<string, string>): Observable<MetricsResponse> {
    return this.api.get<MetricsResponse>('/metrics', params);
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
