import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { WorkflowService, UserService } from '../../../core/services';
import { UserInfo } from '../../../core/services/settings.service';
import { WorkflowShare } from '../../../core/models';

@Component({
    selector: 'app-workflow-share-modal',
    imports: [CommonModule, FormsModule],
    template: `
    @if (isOpen) {
      <div class="modal-backdrop" (click)="closed.emit()"></div>
      <div class="modal-dialog">
        <div class="modal-header">
          <h3>Share "{{ workflowName }}"</h3>
          <button class="btn-close-modal" (click)="closed.emit()">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <!-- Add user row -->
          <div class="add-user-row">
            <div class="search-wrapper">
              <input class="search-input" type="text" placeholder="Search by name or email..."
                     [ngModel]="searchTerm" (ngModelChange)="onSearchChange($event)">
              @if (searchResults().length > 0) {
                <div class="search-dropdown">
                  @for (user of searchResults(); track user.id) {
                    <button class="search-result-item" (click)="selectUser(user)">
                      <span class="result-name">{{ user.firstName || '' }} {{ user.lastName || '' }}</span>
                      <span class="result-email">{{ user.email }}</span>
                    </button>
                  }
                </div>
              }
            </div>
            <select class="permission-select" [(ngModel)]="newPermission">
              <option value="VIEW">View</option>
              <option value="EDIT">Edit</option>
            </select>
            <button class="btn-add" [disabled]="!selectedUser()" (click)="addShare()">Add</button>
          </div>

          @if (selectedUser(); as user) {
            <div class="selected-user">
              Selected: {{ user.firstName || '' }} {{ user.lastName || '' }} ({{ user.email }})
              <button class="btn-clear" (click)="selectedUser.set(null)">&times;</button>
            </div>
          }

          <!-- Current shares -->
          <div class="shares-section">
            <h4 class="shares-title">Shared with</h4>
            @if (loading()) {
              <div class="loading-state">Loading...</div>
            } @else if (shares().length === 0) {
              <div class="empty-state">Not shared with anyone yet</div>
            } @else {
              <div class="shares-list">
                @for (share of shares(); track share.id) {
                  <div class="share-row">
                    <div class="share-user-info">
                      <span class="share-name">{{ share.firstName || '' }} {{ share.lastName || '' }}</span>
                      <span class="share-email">{{ share.email }}</span>
                    </div>
                    <select class="permission-select small" [ngModel]="share.permission"
                            (ngModelChange)="onPermissionChange(share, $event)">
                      <option value="VIEW">View</option>
                      <option value="EDIT">Edit</option>
                    </select>
                    <button class="btn-remove" (click)="removeShare(share)">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3 6 5 6 21 6"/>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                      </svg>
                    </button>
                  </div>
                }
              </div>
            }
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-done" (click)="closed.emit()">Done</button>
        </div>
      </div>
    }
  `,
    styles: [`
    .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 1050; }
    .modal-dialog {
      position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
      background: var(--bg-surface, #fff); border-radius: 12px; width: 520px; max-height: 80vh;
      display: flex; flex-direction: column; z-index: 1051; box-shadow: 0 8px 32px rgba(0,0,0,0.2);
    }
    .modal-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 16px 20px; border-bottom: 1px solid var(--border-color, #e5e5e5);
    }
    .modal-header h3 { margin: 0; font-size: 16px; font-weight: 600; }
    .btn-close-modal {
      background: none; border: none; cursor: pointer; padding: 4px;
      color: var(--text-secondary, #666); border-radius: 4px;
    }
    .btn-close-modal:hover { background: var(--bg-hover, #f0f0f0); }
    .modal-body { padding: 16px 20px; overflow-y: auto; flex: 1; }
    .add-user-row { display: flex; gap: 8px; align-items: flex-start; }
    .search-wrapper { flex: 1; position: relative; }
    .search-input {
      width: 100%; padding: 8px 12px; border: 1px solid var(--border-color, #ddd);
      border-radius: 8px; font-size: 13px; outline: none; box-sizing: border-box;
    }
    .search-input:focus { border-color: var(--bs-primary, #7c5cfc); }
    .search-dropdown {
      position: absolute; top: 100%; left: 0; right: 0; margin-top: 4px;
      background: var(--bg-surface, #fff); border: 1px solid var(--border-color, #ddd);
      border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.12); z-index: 10;
      max-height: 200px; overflow-y: auto;
    }
    .search-result-item {
      display: flex; flex-direction: column; gap: 2px; width: 100%; padding: 8px 12px;
      background: none; border: none; cursor: pointer; text-align: left;
    }
    .search-result-item:hover { background: var(--bg-hover, #f5f5f5); }
    .result-name { font-size: 13px; font-weight: 500; }
    .result-email { font-size: 12px; color: var(--text-secondary, #888); }
    .permission-select {
      padding: 8px 10px; border: 1px solid var(--border-color, #ddd); border-radius: 8px;
      font-size: 13px; background: var(--bg-surface, #fff); cursor: pointer;
    }
    .permission-select.small { padding: 4px 8px; font-size: 12px; }
    .btn-add {
      padding: 8px 14px; border: none; border-radius: 8px;
      background: var(--bs-primary, #7c5cfc); color: #fff; font-size: 13px;
      font-weight: 500; cursor: pointer; white-space: nowrap;
    }
    .btn-add:disabled { opacity: 0.5; cursor: not-allowed; }
    .btn-add:not(:disabled):hover { filter: brightness(1.1); }
    .selected-user {
      margin-top: 8px; padding: 6px 10px; background: color-mix(in srgb, var(--bs-primary, #7c5cfc) 8%, transparent);
      border-radius: 6px; font-size: 12px; display: flex; align-items: center; gap: 8px;
    }
    .btn-clear { background: none; border: none; cursor: pointer; font-size: 16px; color: var(--text-secondary, #666); }
    .shares-section { margin-top: 20px; }
    .shares-title { font-size: 13px; font-weight: 600; margin: 0 0 10px; color: var(--text-secondary, #666); text-transform: uppercase; letter-spacing: 0.5px; }
    .loading-state, .empty-state { text-align: center; padding: 16px; color: var(--text-secondary, #888); font-size: 13px; }
    .shares-list { display: flex; flex-direction: column; gap: 6px; }
    .share-row {
      display: flex; align-items: center; gap: 8px; padding: 8px 10px;
      border: 1px solid var(--border-color, #eee); border-radius: 8px;
    }
    .share-user-info { flex: 1; display: flex; flex-direction: column; gap: 1px; }
    .share-name { font-size: 13px; font-weight: 500; }
    .share-email { font-size: 12px; color: var(--text-secondary, #888); }
    .btn-remove {
      background: none; border: none; cursor: pointer; padding: 4px;
      color: var(--text-secondary, #888); border-radius: 4px;
    }
    .btn-remove:hover { color: var(--bs-danger, #dc3545); background: color-mix(in srgb, var(--bs-danger, #dc3545) 10%, transparent); }
    .modal-footer {
      display: flex; justify-content: flex-end; padding: 12px 20px;
      border-top: 1px solid var(--border-color, #e5e5e5);
    }
    .btn-done {
      padding: 8px 20px; border: none; border-radius: 8px;
      background: var(--bs-primary, #7c5cfc); color: #fff; font-size: 13px;
      font-weight: 500; cursor: pointer;
    }
    .btn-done:hover { filter: brightness(1.1); }
  `]
})
export class WorkflowShareModalComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() workflowId = '';
  @Input() workflowName = '';
  @Output() closed = new EventEmitter<void>();

  searchTerm = '';
  newPermission: 'VIEW' | 'EDIT' = 'VIEW';
  loading = signal(false);
  shares = signal<WorkflowShare[]>([]);
  searchResults = signal<UserInfo[]>([]);
  selectedUser = signal<UserInfo | null>(null);

  private searchSubject = new Subject<string>();

  constructor(
    private workflowService: WorkflowService,
    private userService: UserService
  ) {
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(term => term.length >= 2 ? this.userService.search(term) : of([]))
    ).subscribe(results => this.searchResults.set(results));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen && this.workflowId) {
      this.searchTerm = '';
      this.searchResults.set([]);
      this.selectedUser.set(null);
      this.newPermission = 'VIEW';
      this.loadShares();
    }
  }

  onSearchChange(term: string): void {
    this.searchTerm = term;
    this.searchSubject.next(term);
  }

  selectUser(user: UserInfo): void {
    this.selectedUser.set(user);
    this.searchTerm = '';
    this.searchResults.set([]);
  }

  loadShares(): void {
    this.loading.set(true);
    this.workflowService.getShares(this.workflowId).subscribe({
      next: (shares) => {
        this.shares.set(shares);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  addShare(): void {
    const user = this.selectedUser();
    if (!user) return;
    this.workflowService.addShare(this.workflowId, user.id, this.newPermission).subscribe({
      next: (share) => {
        this.shares.update(list => [...list, share]);
        this.selectedUser.set(null);
        this.searchTerm = '';
      }
    });
  }

  onPermissionChange(share: WorkflowShare, permission: string): void {
    this.workflowService.updateShare(this.workflowId, share.id, permission).subscribe({
      next: (updated) => {
        this.shares.update(list => list.map(s => s.id === updated.id ? updated : s));
      }
    });
  }

  removeShare(share: WorkflowShare): void {
    this.workflowService.removeShare(this.workflowId, share.id).subscribe({
      next: () => {
        this.shares.update(list => list.filter(s => s.id !== share.id));
      }
    });
  }
}
