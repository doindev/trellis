import { Component, Input, Output, EventEmitter, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'app-description-modal',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="modal-backdrop" (click)="onBackdropClick($event)">
      <div class="modal-panel">
        <div class="modal-header">
          <h3 class="modal-title">Workflow Description</h3>
          <button class="modal-close" (click)="onCancel()">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label" for="description">Description</label>
            <textarea class="form-textarea"
                      id="description"
                      [(ngModel)]="descriptionText"
                      rows="6"
                      placeholder="Add a description to help others understand what this workflow does..."
                      autofocus></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" (click)="onCancel()">Cancel</button>
          <button class="btn-save" (click)="onSave()">Save</button>
        </div>
      </div>
    </div>
  `,
    styleUrl: '../publish-modal/publish-modal.component.scss',
    styles: [`
    .btn-save {
      padding: 7px 16px;
      background: hsl(247, 49%, 53%);
      border: 1px solid hsl(247, 49%, 53%);
      color: #fff;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 500;
      cursor: pointer;
    }
    .btn-save:hover {
      background: hsl(247, 49%, 46%);
      border-color: hsl(247, 49%, 46%);
    }
  `]
})
export class DescriptionModalComponent implements OnInit {
  @Input() description = '';
  @Output() saved = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  descriptionText = '';

  ngOnInit(): void {
    this.descriptionText = this.description || '';
  }

  onSave(): void {
    this.saved.emit(this.descriptionText.trim());
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
