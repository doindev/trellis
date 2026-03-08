import { Component, Output, EventEmitter, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'app-import-uri-modal',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="modal-backdrop" (click)="onBackdropClick($event)">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">Import from URI</h3>
          <button class="modal-close" (click)="onCancel()">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label" for="uri">Workflow JSON URL</label>
            <input class="form-input"
                   id="uri"
                   type="url"
                   [(ngModel)]="uri"
                   (keydown.enter)="onImport()"
                   placeholder="https://example.com/workflow.json"
                   autofocus>
          </div>
          @if (errorMessage) {
            <div class="error-message">{{ errorMessage }}</div>
          }
          <p class="help-text">
            Enter a URL pointing to a workflow JSON file. The workflow data will replace the current workflow's nodes and connections.
          </p>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" (click)="onCancel()">Cancel</button>
          <button class="btn-import" [disabled]="!uri.trim() || importing" (click)="onImport()">
            {{ importing ? 'Importing...' : 'Import' }}
          </button>
        </div>
      </div>
    </div>
  `,
    styleUrl: '../publish-modal/publish-modal.component.scss',
    styles: [`
    .btn-import {
      padding: 7px 16px;
      background: hsl(247, 49%, 53%);
      border: 1px solid hsl(247, 49%, 53%);
      color: #fff;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 500;
      cursor: pointer;
    }
    .btn-import:hover:not(:disabled) {
      background: hsl(247, 49%, 46%);
      border-color: hsl(247, 49%, 46%);
    }
    .btn-import:disabled {
      opacity: 0.5;
      cursor: default;
    }
    .help-text {
      font-size: 0.75rem;
      color: hsl(0, 0%, 50%);
      margin: 0;
    }
    .error-message {
      font-size: 0.8125rem;
      color: hsl(355, 83%, 62%);
      padding: 8px 12px;
      background: hsla(355, 83%, 52%, 0.1);
      border-radius: 6px;
    }
  `]
})
export class ImportUriModalComponent {
  @Output() imported = new EventEmitter<any>();
  @Output() cancelled = new EventEmitter<void>();

  uri = '';
  importing = false;
  errorMessage = '';

  onImport(): void {
    if (!this.uri.trim() || this.importing) return;
    this.importing = true;
    this.errorMessage = '';

    fetch(this.uri.trim())
      .then(res => {
        if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        return res.json();
      })
      .then(data => {
        this.importing = false;
        this.imported.emit(data);
      })
      .catch(err => {
        this.importing = false;
        this.errorMessage = `Failed to fetch: ${err.message}`;
      });
  }

  onCancel(): void {
    this.cancelled.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.onCancel();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.onCancel();
  }
}
