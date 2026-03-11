import {
  Component, Input, Output, EventEmitter, ViewChild, ElementRef, AfterViewInit
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { WorkflowService } from '../../../../core/services';

@Component({
    selector: 'app-code-editor-modal',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="code-modal-backdrop" (click)="onBackdropClick($event)">
      <div class="code-modal">
        <!-- Header -->
        <div class="code-modal-header">
          <h4 class="code-modal-title">Code Editor</h4>
          <div class="code-modal-actions">
            <button class="code-btn code-btn-check" (click)="checkCode()" [disabled]="isChecking"
                    title="Check for errors (Ctrl+Shift+C)">
              @if (isChecking) {
                <span class="check-spinner"></span>
              }
              Check
            </button>
            <button class="code-btn code-btn-save" (click)="onSave()" title="Save (Ctrl+Enter)">Save</button>
            <button class="code-btn code-btn-cancel" (click)="onCancel()" title="Cancel (Escape)">Cancel</button>
          </div>
        </div>

        <!-- Body: editor area -->
        <div class="code-modal-body">
          <div class="code-editor-area">
            <div class="line-numbers" #lineNumbers>
              @for (num of lineNumberArray; track num) {
                <span class="line-num">{{ num }}</span>
              }
            </div>
            <textarea #codeTextarea
                      class="code-textarea"
                      [(ngModel)]="currentCode"
                      (ngModelChange)="onCodeChange()"
                      (keydown)="onKeydown($event)"
                      (scroll)="syncScroll()"
                      spellcheck="false"
                      autocomplete="off"
                      autocorrect="off"
                      autocapitalize="off"></textarea>
          </div>
        </div>

        <!-- Footer: compilation status -->
        <div class="code-modal-footer" [class.has-error]="hasError" [class.has-success]="hasSuccess">
          @if (hasError) {
            <div class="code-status-error">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              <span class="code-error-text">{{ validationErrors[0] }}</span>
            </div>
          } @else if (hasSuccess) {
            <div class="code-status-success">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <span>No errors detected</span>
            </div>
          } @else {
            <div class="code-status-idle">
              <span>Press Check or Ctrl+Shift+C to validate</span>
            </div>
          }
        </div>
      </div>
    </div>
  `,
    styles: [`
    .code-modal-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1070;
      background: rgba(0, 0, 0, 0.6);
      display: flex;
      align-items: stretch;
      justify-content: stretch;
    }
    .code-modal {
      position: fixed;
      inset: 60px;
      z-index: 1071;
      background: hsl(0, 0%, 11%);
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      border: 1px solid hsl(0, 0%, 22%);
      overflow: hidden;
    }
    .code-modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      height: 48px;
      padding: 0 16px;
      border-bottom: 1px solid hsl(0, 0%, 22%);
      flex-shrink: 0;
    }
    .code-modal-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: hsl(0, 0%, 90%);
      margin: 0;
    }
    .code-modal-actions {
      display: flex;
      gap: 8px;
    }
    .code-btn {
      padding: 5px 14px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 500;
      border: none;
      cursor: pointer;
      transition: background 0.15s;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .code-btn-check {
      background: hsl(210, 50%, 28%);
      color: hsl(210, 80%, 80%);
    }
    .code-btn-check:hover { background: hsl(210, 50%, 35%); }
    .code-btn-check:disabled { opacity: 0.6; cursor: default; }
    .code-btn-save {
      background: hsl(147, 64%, 32%);
      color: hsl(0, 0%, 96%);
    }
    .code-btn-save:hover { background: hsl(147, 64%, 38%); }
    .code-btn-cancel {
      background: hsl(0, 0%, 20%);
      color: hsl(0, 0%, 80%);
    }
    .code-btn-cancel:hover { background: hsl(0, 0%, 26%); }

    .check-spinner {
      width: 12px;
      height: 12px;
      border: 2px solid hsl(210, 80%, 80%);
      border-top-color: transparent;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    /* Editor body */
    .code-modal-body {
      flex: 1;
      overflow: hidden;
    }
    .code-editor-area {
      display: flex;
      height: 100%;
    }
    .line-numbers {
      display: flex;
      flex-direction: column;
      padding: 10px 0;
      min-width: 48px;
      background: hsl(0, 0%, 9%);
      border-right: 1px solid hsl(0, 0%, 20%);
      overflow: hidden;
      user-select: none;
      flex-shrink: 0;
    }
    .line-num {
      display: block;
      padding: 0 12px 0 8px;
      text-align: right;
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.875rem;
      line-height: 1.5;
      color: hsl(0, 0%, 38%);
      height: 1.5em;
    }
    .code-textarea {
      flex: 1;
      width: 100%;
      background: hsl(0, 0%, 9%);
      border: none;
      color: hsl(0, 0%, 90%);
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.875rem;
      line-height: 1.5;
      padding: 10px 12px;
      resize: none;
      outline: none;
      overflow-y: auto;
      white-space: pre;
      tab-size: 2;
    }
    .code-textarea::placeholder { color: hsl(0, 0%, 34%); }

    /* Footer */
    .code-modal-footer {
      height: 36px;
      display: flex;
      align-items: center;
      padding: 0 16px;
      border-top: 1px solid hsl(0, 0%, 22%);
      flex-shrink: 0;
      font-size: 0.75rem;
    }
    .code-status-idle {
      color: hsl(0, 0%, 46%);
    }
    .code-status-success {
      display: flex;
      align-items: center;
      gap: 6px;
      color: hsl(147, 64%, 55%);
    }
    .code-status-error {
      display: flex;
      align-items: center;
      gap: 6px;
      color: hsl(0, 72%, 65%);
    }
    .code-error-text {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.75rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .code-modal-footer.has-error {
      background: hsla(0, 72%, 50%, 0.08);
    }
    .code-modal-footer.has-success {
      background: hsla(147, 64%, 40%, 0.08);
    }
  `]
})
export class CodeEditorModalComponent implements AfterViewInit {
  @Input() code = '';
  @Input() language = 'javaScript';
  @Input() paramName = '';

  @Output() codeSaved = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();

  @ViewChild('codeTextarea') codeTextarea?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('lineNumbers') lineNumbersEl?: ElementRef<HTMLDivElement>;

  currentCode = '';
  isChecking = false;
  validationErrors: string[] = [];
  validationStatus: 'idle' | 'success' | 'error' = 'idle';

  constructor(private workflowService: WorkflowService) {}

  ngAfterViewInit(): void {
    this.currentCode = this.code;
    setTimeout(() => {
      this.codeTextarea?.nativeElement.focus();
    });
  }

  get lineNumberArray(): number[] {
    const count = (this.currentCode || '').split('\n').length;
    return Array.from({ length: count }, (_, i) => i + 1);
  }

  get hasError(): boolean {
    return this.validationStatus === 'error';
  }

  get hasSuccess(): boolean {
    return this.validationStatus === 'success';
  }

  onCodeChange(): void {
    // Reset validation status when code changes
    this.validationStatus = 'idle';
    this.validationErrors = [];
  }

  syncScroll(): void {
    const ta = this.codeTextarea?.nativeElement;
    const ln = this.lineNumbersEl?.nativeElement;
    if (ta && ln) {
      ln.scrollTop = ta.scrollTop;
    }
  }

  checkCode(): void {
    if (this.isChecking) return;
    this.isChecking = true;
    this.validationStatus = 'idle';
    this.validationErrors = [];

    this.workflowService.validateCode(this.currentCode, this.language).subscribe({
      next: (res) => {
        this.isChecking = false;
        if (res.valid) {
          this.validationStatus = 'success';
          this.validationErrors = [];
        } else {
          this.validationStatus = 'error';
          this.validationErrors = res.errors || ['Unknown error'];
        }
      },
      error: (err) => {
        this.isChecking = false;
        this.validationStatus = 'error';
        this.validationErrors = [err.message || 'Validation request failed'];
      }
    });
  }

  onKeydown(event: KeyboardEvent): void {
    // Tab: insert 2 spaces
    if (event.key === 'Tab') {
      event.preventDefault();
      const ta = this.codeTextarea?.nativeElement;
      if (!ta) return;
      const start = ta.selectionStart;
      const end = ta.selectionEnd;
      const before = this.currentCode.substring(0, start);
      const after = this.currentCode.substring(end);
      this.currentCode = before + '  ' + after;
      setTimeout(() => {
        ta.selectionStart = ta.selectionEnd = start + 2;
      });
      return;
    }

    // Ctrl+Enter: Save
    if (event.key === 'Enter' && event.ctrlKey) {
      event.preventDefault();
      this.onSave();
      return;
    }

    // Escape: Cancel
    if (event.key === 'Escape') {
      event.preventDefault();
      this.onCancel();
      return;
    }

    // Ctrl+Shift+C: Check
    if (event.key === 'C' && event.ctrlKey && event.shiftKey) {
      event.preventDefault();
      this.checkCode();
      return;
    }
  }

  onSave(): void {
    this.codeSaved.emit(this.currentCode);
  }

  onCancel(): void {
    this.closed.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('code-modal-backdrop')) {
      this.closed.emit();
    }
  }
}
