import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { UserInfo } from './settings.service';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly path = '/users';

  constructor(private api: ApiService) {}

  list(): Observable<UserInfo[]> {
    return this.api.get<UserInfo[]>(this.path);
  }

  search(term: string): Observable<UserInfo[]> {
    return this.api.get<UserInfo[]>(this.path, { search: term });
  }
}
