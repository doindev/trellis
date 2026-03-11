import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
    selector: 'app-string-param',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="param-header">
      <label class="param-label">
        {{ param.displayName }}
        @if (param.required) { <span class="required">*</span> }
      </label>
      @if (!param.noDataExpression) {
        <div class="expr-radio-group" role="radiogroup">
          <label class="expr-radio" [class.active]="!isExpression" (click)="setFixed()">
            <div class="expr-radio-btn">Fixed</div>
          </label>
          <label class="expr-radio" [class.active]="isExpression" (click)="setExpression()">
            <div class="expr-radio-btn">Expression</div>
          </label>
        </div>
      }
    </div>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    @if (isExpression) {
      <div class="expr-input-wrapper">
        <input type="text"
               class="form-control param-input expr-input"
               [ngModel]="value"
               (ngModelChange)="valueChange.emit($event)"
               (blur)="blurred.emit()"
               (focus)="focused.emit()"
               [placeholder]="'e.g. Hello {{$json.name}}'"
               [disabled]="readOnly"
               (dragover)="onDragOver($event)"
               (drop)="onDrop($event)">
        <button class="expr-editor-btn" (mousedown)="$event.preventDefault(); openExpressionEditor.emit()" title="Open expression editor">
          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M7 8l-4 4 4 4"/><path d="M17 8l4 4-4 4"/>
          </svg>
        </button>
      </div>
      @if (expressionError) {
        <div class="expr-error-msg">{{ expressionError }}</div>
      }
    } @else if (isMultiline) {
      <div class="code-textarea-wrapper">
        @if (isCodeEditor) {
          <button class="code-editor-btn" (click)="openCodeEditor.emit()" title="Open code editor">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9l-2 3 2 3"/><path d="M15 9l2 3-2 3"/>
            </svg>
          </button>
        }
        <textarea class="form-control param-input"
                  [ngModel]="value"
                  (ngModelChange)="valueChange.emit($event)"
                  (blur)="blurred.emit()"
                  [placeholder]="param.placeHolder || ''"
                  [disabled]="readOnly"
                  [attr.rows]="param.typeOptions?.rows || 4"
                  (dragover)="onDragOver($event)"
                  (drop)="onDrop($event)"></textarea>
      </div>
    } @else {
      <input type="text"
             class="form-control param-input"
             [ngModel]="value"
             (ngModelChange)="valueChange.emit($event)"
             (blur)="blurred.emit()"
             [placeholder]="param.placeHolder || ''"
             [disabled]="readOnly"
             (dragover)="onDragOver($event)"
             (drop)="onDrop($event)">
    }
  `,
    styles: [`
    .param-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
    .param-label { font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin: 0; }
    .required { color: var(--cwc-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .param-input {
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      font-size: 0.8125rem;
      border-radius: 6px;
    }
    .param-input::placeholder { color: hsl(0,0%,46%); }
    .param-input:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .expr-input { border-color: hsl(30,80%,50%); font-family: 'Consolas', 'Monaco', monospace; }
    .expr-radio-group {
      display: inline-flex;
      border-radius: 6px;
      overflow: hidden;
      border: 1px solid hsl(0,0%,24%);
      flex-shrink: 0;
    }
    .expr-radio {
      cursor: pointer;
      margin: 0;
    }
    .expr-radio-btn {
      padding: 2px 8px;
      font-size: 0.6875rem;
      font-weight: 500;
      color: hsl(0,0%,58%);
      background: hsl(0,0%,13%);
      transition: all 0.15s;
      user-select: none;
      white-space: nowrap;
    }
    .expr-radio.active .expr-radio-btn {
      background: hsl(0,0%,20%);
      color: hsl(0,0%,96%);
    }
    .expr-radio:hover .expr-radio-btn {
      color: hsl(0,0%,80%);
    }
    .expr-input-wrapper {
      position: relative;
    }
    .expr-input-wrapper .param-input {
      padding-right: 30px;
    }
    .expr-editor-btn {
      position: absolute;
      right: 4px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      background: transparent;
      border: none;
      border-radius: 4px;
      color: hsl(30,80%,50%);
      cursor: pointer;
      opacity: 0.6;
      transition: all 0.15s;
    }
    .expr-editor-btn:hover {
      opacity: 1;
      background: hsla(30,80%,50%,0.15);
    }
    .expr-error-msg {
      font-size: 0.6875rem;
      color: hsl(0,72%,65%);
      margin-top: 3px;
      font-family: 'Consolas','Monaco',monospace;
    }
    .code-textarea-wrapper {
      position: relative;
    }
    .code-textarea-wrapper .param-input {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.8125rem;
      line-height: 1.5;
      padding-right: 36px;
    }
    .code-editor-btn {
      position: absolute;
      right: 6px;
      top: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      background: hsl(0,0%,15%);
      border: 1px solid hsl(0,0%,28%);
      border-radius: 4px;
      color: hsl(0,0%,70%);
      cursor: pointer;
      opacity: 0.7;
      transition: all 0.15s;
      z-index: 1;
    }
    .code-editor-btn:hover {
      opacity: 1;
      background: hsl(0,0%,20%);
      color: hsl(0,0%,96%);
      border-color: hsl(247,49%,53%);
    }
  `]
})
export class StringParamComponent {
  @Input() param!: NodeParameter;
  @Input() value: any = '';
  @Input() readOnly = false;
  @Input() expressionError = '';
  @Output() valueChange = new EventEmitter<any>();
  @Output() blurred = new EventEmitter<void>();
  @Output() focused = new EventEmitter<void>();
  @Output() openExpressionEditor = new EventEmitter<void>();
  @Output() openCodeEditor = new EventEmitter<void>();

  /** Manual toggle — set when user clicks "Expression" on a field that has no {{ }} yet */
  expressionMode = false;

  get isExpression(): boolean {
    const str = String(this.value ?? '');
    return str.includes('{{') || this.expressionMode;
  }

  get isMultiline(): boolean {
    return this.param.typeOptions?.rows > 1 || this.param.type === 'string' && this.param.typeOptions?.editor === 'code';
  }

  get isCodeEditor(): boolean {
    return this.param.typeOptions?.editor === 'codeNodeEditor';
  }

  setFixed(): void {
    this.expressionMode = false;
  }

  setExpression(): void {
    this.expressionMode = true;
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'copy';
    }
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    const data = event.dataTransfer?.getData('text/plain');
    if (!data || (!data.includes('$json.') && !data.includes('$node['))) return;

    const input = event.target as HTMLInputElement | HTMLTextAreaElement;
    const currentVal = String(this.value ?? '');
    const pos = input?.selectionStart ?? currentVal.length;
    const before = currentVal.substring(0, pos);
    const after = currentVal.substring(pos);
    this.valueChange.emit(before + data + after);
  }
}
