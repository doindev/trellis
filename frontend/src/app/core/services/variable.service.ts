import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { Variable } from '../models';

@Injectable({ providedIn: 'root' })
export class VariableService {
  private readonly path = '/variables';

  constructor(private api: ApiService) {}

  list(): Observable<Variable[]> {
    return this.api.get<Variable[]>(this.path);
  }

  get(id: string): Observable<Variable> {
    return this.api.get<Variable>(`${this.path}/${id}`);
  }

  create(data: Partial<Variable>): Observable<Variable> {
    return this.api.post<Variable>(this.path, data);
  }

  update(id: string, data: Partial<Variable>): Observable<Variable> {
    return this.api.put<Variable>(`${this.path}/${id}`, data);
  }

  delete(id: string): Observable<void> {
    return this.api.delete<void>(`${this.path}/${id}`);
  }
}
