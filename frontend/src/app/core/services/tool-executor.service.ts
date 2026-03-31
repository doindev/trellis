import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, of, throwError, timer, TimeoutError } from 'rxjs';
import { catchError, map, switchMap, takeWhile, timeout } from 'rxjs/operators';

export interface ApiCallSpec {
  method: string;
  path: string;
  body?: any;
  queryParams?: Record<string, string>;
  category: string;
  requiresPolling: boolean;
  pollingPath?: string;
}

/**
 * Executes REST API calls on behalf of the MCP tool system using the user's session.
 */
@Injectable({ providedIn: 'root' })
export class ToolExecutorService {
  private readonly baseUrl = ((window as any).__CWC_BASE_PATH__ || '') + '';

  constructor(private http: HttpClient) {}

  execute(apiSpec: ApiCallSpec): Observable<any> {
    const request$ = apiSpec.requiresPolling
      ? this.executeWithPolling(apiSpec)
      : this.executeRequest(apiSpec);

    return request$.pipe(
      timeout(55000), // 55s timeout (backend consent times out at 60s)
      catchError(err => {
        if (err instanceof TimeoutError) {
          return throwError(() => new Error('Browser API call timed out'));
        }
        const msg = err?.error?.message || err?.message || 'Request failed';
        return throwError(() => new Error(msg));
      })
    );
  }

  private executeRequest(apiSpec: ApiCallSpec): Observable<any> {
    const url = this.baseUrl + apiSpec.path;
    let params = new HttpParams();
    if (apiSpec.queryParams) {
      Object.entries(apiSpec.queryParams).forEach(([key, value]) => {
        if (value != null) params = params.set(key, value);
      });
    }

    switch (apiSpec.method.toUpperCase()) {
      case 'GET':
        return this.http.get<any>(url, { params });
      case 'POST':
        return this.http.post<any>(url, apiSpec.body || {}, { params });
      case 'PUT':
        return this.http.put<any>(url, apiSpec.body || {}, { params });
      case 'PATCH':
        return this.http.patch<any>(url, apiSpec.body || {}, { params });
      case 'DELETE':
        return this.http.delete<any>(url, { params });
      default:
        return throwError(() => new Error('Unsupported HTTP method: ' + apiSpec.method));
    }
  }

  /**
   * For cwc_execute_workflow: POST to start, then poll GET every 2s for up to 60s.
   */
  private executeWithPolling(apiSpec: ApiCallSpec): Observable<any> {
    return this.executeRequest(apiSpec).pipe(
      switchMap((startResult: any) => {
        const executionId = startResult?.executionId || startResult?.id;
        if (!executionId || !apiSpec.pollingPath) {
          return of(startResult);
        }

        const pollPath = apiSpec.pollingPath.replace('{executionId}', executionId);
        const pollUrl = this.baseUrl + pollPath;
        const maxAttempts = 30; // 60 seconds at 2s intervals
        let attempt = 0;

        return timer(0, 2000).pipe(
          switchMap(() => this.http.get<any>(pollUrl)),
          takeWhile((result: any) => {
            attempt++;
            const status = result?.status?.toLowerCase();
            if (status === 'running' || status === 'new' || status === 'waiting') {
              return attempt < maxAttempts;
            }
            return false;
          }, true),
          map((result: any) => result)
        );
      })
    );
  }
}
