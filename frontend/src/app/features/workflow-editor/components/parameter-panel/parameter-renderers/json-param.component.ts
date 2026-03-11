import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NodeParameter } from '../../../../../core/models';

@Component({
    selector: 'app-json-param',
    imports: [CommonModule, FormsModule],
    template: `
    <label class="param-label">
      {{ param.displayName }}
      @if (param.required) { <span class="required">*</span> }
    </label>
    @if (param.description) {
      <p class="param-description">{{ param.description }}</p>
    }
    <div class="json-textarea-wrapper">
      @if (showEditorBtn) {
        <button class="json-editor-btn" (click)="openSchemaEditor.emit()" title="Edit schema">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 9l-2 3 2 3"/><path d="M15 9l2 3-2 3"/>
          </svg>
        </button>
      }
      <textarea class="form-control param-input json-editor"
                [class.has-editor-btn]="showEditorBtn"
                [ngModel]="jsonString"
                (ngModelChange)="onJsonChange($event)"
                [class.is-invalid]="hasError"
                rows="6"
                spellcheck="false"
                [placeholder]="param.placeHolder || '{}'"
                [disabled]="readOnly"></textarea>
    </div>
    @if (hasError) {
      <div class="invalid-feedback d-block">Invalid JSON</div>
    }
  `,
    styles: [`
    .param-label { display: block; font-size: 0.8125rem; font-weight: 500; color: hsl(0,0%,96%); margin-bottom: 4px; }
    .required { color: var(--cwc-error-color); }
    .param-description { font-size: 0.6875rem; color: hsl(0,0%,58%); margin-bottom: 6px; }
    .json-textarea-wrapper { position: relative; }
    .json-editor {
      font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
      font-size: 0.75rem;
      line-height: 1.5;
      resize: vertical;
      background: hsl(0,0%,9%);
      border: 1px solid hsl(0,0%,24%);
      color: hsl(0,0%,96%);
      border-radius: 6px;
    }
    .json-editor.has-editor-btn { padding-right: 36px; }
    .json-editor:focus { background: hsl(0,0%,9%); border-color: hsl(247,49%,53%); box-shadow: 0 0 0 2px hsla(247,49%,53%,0.15); color: hsl(0,0%,96%); }
    .json-editor.is-invalid { border-color: var(--cwc-error-color); }
    .invalid-feedback { font-size: 0.6875rem; }
    .json-editor-btn {
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
    .json-editor-btn:hover {
      opacity: 1;
      background: hsl(0,0%,20%);
      color: hsl(0,0%,96%);
      border-color: hsl(247,49%,53%);
    }
  `]
})
export class JsonParamComponent {
  @Input() param!: NodeParameter;
  @Input() readOnly = false;
  @Input() showEditorBtn = false;
  @Output() openSchemaEditor = new EventEmitter<void>();
  @Input() set value(val: any) {
    if (typeof val === 'object') {
      this.jsonString = JSON.stringify(val, null, 2);
    } else {
      this.jsonString = val || '';
    }
  }
  @Output() valueChange = new EventEmitter<any>();

  jsonString = '';
  hasError = false;

  onJsonChange(str: string): void {
    this.jsonString = str;
    try {
      const parsed = JSON.parse(str);
      this.hasError = false;
      this.valueChange.emit(parsed);
    } catch {
      this.hasError = str.trim().length > 0;
    }
  }
}
