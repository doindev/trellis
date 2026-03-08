import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-confirm-dialog',
    imports: [CommonModule],
    template: `
    @if (isOpen) {
      <div class="modal-backdrop" (click)="onCancel()"></div>
      <div class="modal d-block" tabindex="-1">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">{{ title }}</h5>
              <button type="button" class="btn-close" (click)="onCancel()"></button>
            </div>
            <div class="modal-body">
              <p>{{ message }}</p>
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-secondary" (click)="onCancel()">Cancel</button>
              <button type="button" class="btn" [class]="confirmBtnClass" (click)="onConfirm()">
                {{ confirmText }}
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
    styles: [`
    .modal-backdrop {
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: hsla(0, 0%, 0%, 0.7);
      z-index: 1040;
    }
    .modal {
      z-index: 1050;
    }
    .modal-content {
      background: hsl(0, 0%, 13%);
      color: hsl(0, 0%, 96%);
      border: 1px solid hsl(0, 0%, 24%);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.4);
    }
    .modal-header {
      border-bottom-color: hsl(0, 0%, 24%);
    }
    .modal-footer {
      border-top-color: hsl(0, 0%, 24%);
    }
    .modal-body p {
      color: hsl(0, 0%, 68%);
    }
    .btn-close {
      filter: invert(1) grayscale(100%) brightness(200%);
    }
  `]
})
export class ConfirmDialogComponent {
  @Input() isOpen = false;
  @Input() title = 'Confirm';
  @Input() message = 'Are you sure?';
  @Input() confirmText = 'Confirm';
  @Input() confirmBtnClass = 'btn-danger';
  @Output() confirmed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  onConfirm(): void {
    this.confirmed.emit();
  }

  onCancel(): void {
    this.cancelled.emit();
  }
}
