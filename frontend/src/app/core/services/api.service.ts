import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, catchError, throwError } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = ((window as any).__CWC_BASE_PATH__ || '') + '/api';

  constructor(private http: HttpClient) {}

  get<T>(path: string, params?: Record<string, string>): Observable<T> {
    let httpParams = new HttpParams();
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        httpParams = httpParams.set(key, value);
      });
    }
    return this.http
      .get<T>(`${this.baseUrl}${path}`, { params: httpParams })
      .pipe(catchError(this.handleError));
  }

  post<T>(path: string, body: any = {}): Observable<T> {
    return this.http
      .post<T>(`${this.baseUrl}${path}`, body)
      .pipe(catchError(this.handleError));
  }

  put<T>(path: string, body: any = {}): Observable<T> {
    return this.http
      .put<T>(`${this.baseUrl}${path}`, body)
      .pipe(catchError(this.handleError));
  }

  delete<T>(path: string): Observable<T> {
    return this.http
      .delete<T>(`${this.baseUrl}${path}`)
      .pipe(catchError(this.handleError));
  }

  patch<T>(path: string, body: any = {}): Observable<T> {
    return this.http
      .patch<T>(`${this.baseUrl}${path}`, body)
      .pipe(catchError(this.handleError));
  }

  postFormData<T>(path: string, formData: FormData): Observable<T> {
    return this.http
      .post<T>(`${this.baseUrl}${path}`, formData)
      .pipe(catchError(this.handleError));
  }

  private handleError(error: any): Observable<never> {
    let message = 'An unexpected error occurred';
    if (error.error?.message) {
      message = error.error.message;
    } else if (error.message) {
      message = error.message;
    }
    console.error('API Error:', error);
    return throwError(() => new Error(message));
  }
}
