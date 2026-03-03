import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, ElementRef, ViewChild, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WorkflowService } from '../../../../core/services/workflow.service';
import { WorkflowVersion, Page } from '../../../../core/models';

type VersionFilter = 'all' | 'published' | 'saves';

@Component({
  selector: 'app-version-history-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="version-panel">
      <div class="version-panel-header">
        <h3>Version History</h3>
        <button class="close-btn" (click)="close.emit()" title="Close">&times;</button>
      </div>

      <div class="filter-bar">
        <div class="btn-group btn-group-sm">
          <button class="btn" [class.active]="activeFilter === 'all'" (click)="setFilter('all')">All</button>
          <button class="btn" [class.active]="activeFilter === 'published'" (click)="setFilter('published')">Published</button>
          <button class="btn" [class.active]="activeFilter === 'saves'" (click)="setFilter('saves')">Saves</button>
        </div>
      </div>

      <div class="version-list" #scrollContainer (scroll)="onScroll()">
        @if (versions.length === 0 && !loading) {
          <div class="empty-state">
            @if (activeFilter === 'published') {
              No published versions yet
            } @else if (activeFilter === 'saves') {
              No save revisions yet
            } @else {
              No versions yet
            }
          </div>
        }

        @for (v of versions; track v.id) {
          <div class="version-item" [class.loading]="loadingVersionId === v.id">
            <div class="version-item-body" (click)="onVersionClick(v)">
              <div class="version-item-row">
                <span class="version-dot" [class.dot-published]="v.published" [class.dot-save]="!v.published"></span>
                <div class="version-item-info">
                  @if (v.published) {
                    <span class="version-name">{{ v.versionName || 'Published' }}</span>
                    <span class="version-number">v{{ v.versionNumber }}</span>
                  } @else {
                    <span class="version-name save-name">Auto-save</span>
                    <span class="version-time-inline">{{ getRelativeTime(v.publishedAt) }}</span>
                  }
                </div>
              </div>
              @if (v.published && v.description) {
                <div class="version-desc">{{ v.description | slice:0:120 }}{{ v.description!.length > 120 ? '...' : '' }}</div>
              }
              @if (v.published) {
                <div class="version-time">{{ getRelativeTime(v.publishedAt) }}</div>
              }
            </div>
            <div class="version-item-actions">
              <button class="ellipsis-btn" (click)="toggleMenu($event, v.id)" title="Actions">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                  <circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/>
                </svg>
              </button>
            </div>
          </div>
        }

        @if (loading) {
          <div class="loading-indicator">
            <div class="spinner"></div>
          </div>
        }
      </div>

      @if (openMenuId) {
        <div class="menu-backdrop" (click)="closeMenu()"></div>
        <div class="context-menu"
             [style.top.px]="menuTop"
             [style.right.px]="menuRight"
             (mouseenter)="cancelMenuClose()"
             (mouseleave)="startMenuCloseTimer()">
          @if (!isPersonalProject) {
            <button class="context-menu-item" (click)="onPublishVersion()">Publish this version</button>
          }
          <button class="context-menu-item" (click)="onCloneVersion()">Clone to new workflow</button>
          <button class="context-menu-item" (click)="onOpenInNewTab()">Open in new tab</button>
          <button class="context-menu-item" (click)="onDownloadVersion()">Download</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .version-panel {
      width: 385px;
      height: 100%;
      background: hsl(0, 0%, 13%);
      border-left: 1px solid hsl(0, 0%, 20%);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      position: relative;
    }

    .version-panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px 18px;
      border-bottom: 1px solid hsl(0, 0%, 20%);
      flex-shrink: 0;

      h3 {
        margin: 0;
        font-size: 0.875rem;
        font-weight: 600;
        color: hsl(0, 0%, 90%);
      }

      .close-btn {
        background: none;
        border: none;
        color: hsl(0, 0%, 50%);
        font-size: 1.25rem;
        cursor: pointer;
        padding: 2px 6px;
        border-radius: 4px;
        line-height: 1;

        &:hover {
          color: hsl(0, 0%, 96%);
          background: hsl(0, 0%, 17%);
        }
      }
    }

    .filter-bar {
      padding: 10px 14px;
      border-bottom: 1px solid hsl(0, 0%, 20%);
      flex-shrink: 0;
    }

    .btn-group {
      display: flex;
      width: 100%;

      .btn {
        flex: 1;
        padding: 5px 8px;
        font-size: 0.75rem;
        font-weight: 500;
        border: 1px solid hsl(0, 0%, 26%);
        background: hsl(0, 0%, 16%);
        color: hsl(0, 0%, 60%);
        cursor: pointer;
        transition: background-color 0.15s, color 0.15s, border-color 0.15s;

        &:first-child {
          border-radius: 6px 0 0 6px;
        }
        &:last-child {
          border-radius: 0 6px 6px 0;
        }
        &:not(:first-child) {
          border-left: none;
        }

        &:hover {
          background: hsl(0, 0%, 20%);
          color: hsl(0, 0%, 80%);
        }

        &.active {
          background: hsl(30, 80%, 50%);
          border-color: hsl(30, 80%, 50%);
          color: hsl(0, 0%, 100%);
        }
      }
    }

    .version-list {
      flex: 1;
      overflow-y: auto;
      padding: 8px;
    }

    .empty-state {
      padding: 32px 16px;
      text-align: center;
      color: hsl(0, 0%, 45%);
      font-size: 0.8125rem;
    }

    .version-item {
      display: flex;
      align-items: stretch;
      background: hsl(0, 0%, 16%);
      border: 1px solid hsl(0, 0%, 22%);
      border-radius: 8px;
      margin-bottom: 6px;
      transition: background-color 0.15s, border-color 0.15s;

      &:hover {
        background: hsl(0, 0%, 19%);
        border-color: hsl(0, 0%, 30%);

        .ellipsis-btn {
          opacity: 1;
        }
      }

      &.loading {
        opacity: 0.6;
        pointer-events: none;
      }
    }

    .version-item-body {
      flex: 1;
      min-width: 0;
      padding: 10px 0 10px 12px;
      cursor: pointer;
    }

    .version-item-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .version-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      flex-shrink: 0;

      &.dot-published {
        background: hsl(145, 60%, 45%);
      }
      &.dot-save {
        background: hsl(30, 80%, 55%);
      }
    }

    .version-item-info {
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
      min-width: 0;
    }

    .version-name {
      font-size: 0.8125rem;
      font-weight: 600;
      color: hsl(0, 0%, 92%);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      &.save-name {
        color: hsl(0, 0%, 65%);
        font-weight: 500;
      }
    }

    .version-number {
      font-size: 0.6875rem;
      color: hsl(145, 60%, 45%);
      font-weight: 600;
      flex-shrink: 0;
    }

    .version-time-inline {
      font-size: 0.6875rem;
      color: hsl(0, 0%, 42%);
      flex-shrink: 0;
    }

    .version-desc {
      font-size: 0.75rem;
      color: hsl(0, 0%, 55%);
      line-height: 1.4;
      margin: 4px 0 0 16px;
    }

    .version-time {
      font-size: 0.6875rem;
      color: hsl(0, 0%, 42%);
      margin: 4px 0 0 16px;
    }

    .version-item-actions {
      display: flex;
      align-items: flex-start;
      padding: 8px 6px 8px 0;
    }

    .ellipsis-btn {
      background: none;
      border: none;
      color: hsl(0, 0%, 50%);
      cursor: pointer;
      padding: 4px 6px;
      border-radius: 4px;
      opacity: 0;
      transition: opacity 0.15s, color 0.15s, background-color 0.15s;

      &:hover {
        color: hsl(0, 0%, 96%);
        background: hsl(0, 0%, 22%);
      }
    }

    .menu-backdrop {
      position: fixed;
      inset: 0;
      z-index: 99;
    }

    .context-menu {
      position: absolute;
      z-index: 100;
      background: hsl(0, 0%, 17%);
      border: 1px solid hsl(0, 0%, 26%);
      border-radius: 8px;
      padding: 4px 0;
      min-width: 190px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
    }

    .context-menu-item {
      display: block;
      width: 100%;
      padding: 8px 14px;
      background: none;
      border: none;
      text-align: left;
      color: hsl(0, 0%, 80%);
      font-size: 0.8125rem;
      cursor: pointer;
      transition: background-color 0.12s, color 0.12s;

      &:hover {
        background: hsl(0, 0%, 22%);
        color: hsl(0, 0%, 96%);
      }
    }

    .loading-indicator {
      display: flex;
      justify-content: center;
      padding: 16px;
    }

    .spinner {
      width: 20px;
      height: 20px;
      border: 2px solid hsl(0, 0%, 25%);
      border-top-color: hsl(30, 80%, 55%);
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class VersionHistoryPanelComponent implements OnInit, OnChanges {
  @Input() workflowId = '';
  @Input() visible = false;
  @Input() isPersonalProject = false;
  @Output() close = new EventEmitter<void>();
  @Output() versionSelected = new EventEmitter<WorkflowVersion>();
  @Output() publishVersion = new EventEmitter<string>();
  @Output() cloneVersion = new EventEmitter<string>();
  @ViewChild('scrollContainer') scrollContainer!: ElementRef<HTMLDivElement>;

  versions: WorkflowVersion[] = [];
  loading = false;
  loadingVersionId: string | null = null;
  activeFilter: VersionFilter = 'all';
  openMenuId: string | null = null;
  menuTop = 0;
  menuRight = 0;
  private currentPage = 0;
  private isLastPage = false;
  private menuCloseTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(private workflowService: WorkflowService) {}

  ngOnInit(): void {
    this.loadPage(0);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['workflowId'] && !changes['workflowId'].firstChange) {
      this.resetAndLoad();
    }
    if (changes['visible'] && this.visible && !changes['visible'].firstChange) {
      this.resetAndLoad();
    }
  }

  setFilter(filter: VersionFilter): void {
    if (this.activeFilter === filter) return;
    this.activeFilter = filter;
    this.resetAndLoad();
  }

  private resetAndLoad(): void {
    this.versions = [];
    this.currentPage = 0;
    this.isLastPage = false;
    this.closeMenu();
    this.loadPage(0);
  }

  private loadPage(page: number): void {
    if (this.loading || this.isLastPage || !this.workflowId) return;
    this.loading = true;
    this.workflowService.getVersions(this.workflowId, page, 20, this.activeFilter).subscribe({
      next: (result: Page<WorkflowVersion>) => {
        this.versions = [...this.versions, ...result.content];
        this.currentPage = result.number;
        this.isLastPage = result.last;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  onScroll(): void {
    const el = this.scrollContainer?.nativeElement;
    if (!el) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100;
    if (nearBottom && !this.loading && !this.isLastPage) {
      this.loadPage(this.currentPage + 1);
    }
  }

  onVersionClick(version: WorkflowVersion): void {
    this.loadingVersionId = version.id;
    this.workflowService.getVersion(this.workflowId, version.id).subscribe({
      next: (full) => {
        this.loadingVersionId = null;
        this.versionSelected.emit(full);
      },
      error: () => {
        this.loadingVersionId = null;
      }
    });
  }

  // --- Context menu ---

  toggleMenu(event: MouseEvent, versionId: string): void {
    event.stopPropagation();
    if (this.openMenuId === versionId) {
      this.closeMenu();
      return;
    }
    this.openMenuId = versionId;

    // Position menu near the button
    const btn = event.currentTarget as HTMLElement;
    const panelEl = btn.closest('.version-panel') as HTMLElement;
    if (panelEl) {
      const panelRect = panelEl.getBoundingClientRect();
      const btnRect = btn.getBoundingClientRect();
      this.menuTop = btnRect.bottom - panelRect.top + 2;
      this.menuRight = panelRect.right - btnRect.right + 4;
    }
  }

  closeMenu(): void {
    this.openMenuId = null;
    this.cancelMenuClose();
  }

  startMenuCloseTimer(): void {
    this.cancelMenuClose();
    this.menuCloseTimer = setTimeout(() => this.closeMenu(), 400);
  }

  cancelMenuClose(): void {
    if (this.menuCloseTimer) {
      clearTimeout(this.menuCloseTimer);
      this.menuCloseTimer = null;
    }
  }

  onPublishVersion(): void {
    if (this.openMenuId) {
      this.publishVersion.emit(this.openMenuId);
      this.closeMenu();
    }
  }

  onCloneVersion(): void {
    if (this.openMenuId) {
      this.cloneVersion.emit(this.openMenuId);
      this.closeMenu();
    }
  }

  onOpenInNewTab(): void {
    if (this.openMenuId) {
      window.open(`/workflow/${this.workflowId}?loadVersionId=${this.openMenuId}`, '_blank');
      this.closeMenu();
    }
  }

  onDownloadVersion(): void {
    if (!this.openMenuId) return;
    const versionId = this.openMenuId;
    this.closeMenu();
    this.workflowService.getVersion(this.workflowId, versionId).subscribe({
      next: (full) => {
        const exportData: Record<string, any> = {
          nodes: full.nodes,
          connections: full.connections,
          settings: full.settings
        };
        if (full.versionName) exportData['versionName'] = full.versionName;
        const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const name = full.versionName || 'save-revision';
        a.download = `${name.replace(/[^a-zA-Z0-9_-]/g, '_')}.json`;
        a.click();
        URL.revokeObjectURL(url);
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.openMenuId) {
      this.closeMenu();
    }
  }

  getRelativeTime(dateStr: string): string {
    const now = Date.now();
    const then = new Date(dateStr).getTime();
    const diff = now - then;
    const seconds = Math.floor(diff / 1000);
    if (seconds < 60) return 'just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}d ago`;
    const months = Math.floor(days / 30);
    if (months < 12) return `${months}mo ago`;
    return `${Math.floor(months / 12)}y ago`;
  }
}
