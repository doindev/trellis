import { Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { ApiService } from './api.service';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private settings$?: Observable<any>;

  constructor(private api: ApiService) {}

  getSettings(): Observable<any> {
    if (!this.settings$) {
      this.settings$ = this.api.get<any>('/settings').pipe(
        shareReplay(1)
      );
    }
    return this.settings$;
  }
}
